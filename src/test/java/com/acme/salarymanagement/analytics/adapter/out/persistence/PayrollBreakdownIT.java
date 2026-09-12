package com.acme.salarymanagement.analytics.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

import com.acme.salarymanagement.analytics.application.port.in.BreakdownDimension;
import com.acme.salarymanagement.analytics.application.port.in.DashboardFilters;
import com.acme.salarymanagement.analytics.application.port.in.GetPayrollBreakdown;
import com.acme.salarymanagement.analytics.application.port.in.PayrollBreakdown;
import com.acme.salarymanagement.shared.CountryCode;
import com.acme.salarymanagement.shared.CurrencyCode;
import com.acme.salarymanagement.shared.JobTitle;
import com.acme.salarymanagement.support.CountingDataSource;
import com.acme.salarymanagement.support.PostgresIntegrationTest;

/**
 * Spend and headcount per group, against a known org in three currencies.
 *
 * <p>Same shape as {@link PayrollSummaryIT} and for the same reason: the figures are small and
 * hand-computable, so the assertions say the number is right rather than that it did not change.
 */
@Transactional
@WithMockUser(roles = "HR_ANALYST")
class PayrollBreakdownIT extends PostgresIntegrationTest {

    private static final String OURS = "Zzzbreakdown";
    private static final CurrencyCode USD = new CurrencyCode("USD");

    @Autowired
    private GetPayrollBreakdown breakdowns;

    @Autowired
    private JdbcTemplate jdbc;

    /**
     * Six people. Two US on 100,000 USD, two German on 100,000 EUR (108,500 USD each), two Indian
     * on 1,000,000 INR (11,900 USD each). Engineering holds one of each market, Finance the other.
     */
    @BeforeEach
    void anOrgInThreeCurrencies() {
        insert("US", "100000.00", "Engineering", "SENIOR");
        insert("US", "100000.00", "Finance", "JUNIOR");
        insert("DE", "100000.00", "Engineering", "SENIOR");
        insert("DE", "100000.00", "Finance", "JUNIOR");
        insert("IN", "1000000.00", "Engineering", "SENIOR");
        insert("IN", "1000000.00", "Finance", "JUNIOR");
    }

    @Test
    void every_group_comes_back_in_one_query_whatever_the_group_count() {
        long forThreeGroups =
                CountingDataSource.statementsIssuedBy(() -> breakdowns.by(BreakdownDimension.COUNTRY, ours()));
        long forTwoGroups =
                CountingDataSource.statementsIssuedBy(() -> breakdowns.by(BreakdownDimension.LEVEL, ours()));

        // Two statements, and the point is that it is two for both: one grouped query however many
        // groups come back, plus one constant read of the rate date. An N+1 is a count that grows
        // with the rows - this does not, which is the property worth pinning. The rate date is a
        // second statement rather than a scalar subquery so that a filter matching nobody still
        // reports which day it converted through (D143).
        assertThat(forThreeGroups).isEqualTo(2);
        assertThat(forTwoGroups).isEqualTo(forThreeGroups);
    }

    @Test
    void each_group_carries_its_own_normalised_total() {
        var byDepartment = breakdowns.by(BreakdownDimension.DEPARTMENT, ours());

        // Each department holds one of each market: 100,000 + 108,500 + 11,900 = 220,400.
        assertThat(groupNamed(byDepartment, "Engineering").headcount()).isEqualTo(3);
        assertThat(groupNamed(byDepartment, "Engineering").totalSpend().amount())
                .isEqualByComparingTo("220400.00");
        assertThat(groupNamed(byDepartment, "Finance").totalSpend().amount()).isEqualByComparingTo("220400.00");
    }

    @Test
    void the_average_within_a_group_is_of_normalised_amounts() {
        var byDepartment = breakdowns.by(BreakdownDimension.DEPARTMENT, ours());

        // 220,400 / 3 = 73,466.67. Averaging the local figures would average rupees with dollars.
        assertThat(groupNamed(byDepartment, "Engineering").averageSalary().amount())
                .isEqualByComparingTo("73466.67");
    }

    @Test
    void grouping_by_country_splits_the_same_people_by_market() {
        var byCountry = breakdowns.by(BreakdownDimension.COUNTRY, ours());

        assertThat(byCountry.rows()).hasSize(3);
        assertThat(groupNamed(byCountry, "DE").totalSpend().amount()).isEqualByComparingTo("217000.00");
        assertThat(groupNamed(byCountry, "IN").totalSpend().amount()).isEqualByComparingTo("23800.00");
        assertThat(groupNamed(byCountry, "US").totalSpend().amount()).isEqualByComparingTo("200000.00");
    }

    @Test
    void grouping_by_level_uses_the_seniority_column() {
        var byLevel = breakdowns.by(BreakdownDimension.LEVEL, ours());

        assertThat(byLevel.rows())
                .extracting(PayrollBreakdown.BreakdownRow::group)
                .containsExactlyInAnyOrder("SENIOR", "JUNIOR");
    }

    @Test
    void the_largest_group_comes_first_so_the_chart_reads_top_down() {
        var byCountry = breakdowns.by(BreakdownDimension.COUNTRY, ours());

        // DE 217,000 > US 200,000 > IN 23,800. A chart ordered by whatever the planner returned
        // reorders itself between filter changes for no reason the reader can see.
        assertThat(byCountry.rows())
                .extracting(PayrollBreakdown.BreakdownRow::group)
                .containsExactly("DE", "US", "IN");
    }

    @Test
    void a_filter_narrows_every_group_together() {
        var germanOnly = ours().withCountry(new CountryCode("DE"));

        var byDepartment = breakdowns.by(BreakdownDimension.DEPARTMENT, germanOnly);

        assertThat(byDepartment.rows()).hasSize(2);
        assertThat(byDepartment.rows()).allSatisfy(row -> {
            assertThat(row.headcount()).isEqualTo(1);
            assertThat(row.totalSpend().amount()).isEqualByComparingTo("108500.00");
        });
    }

    @Test
    void a_filter_nobody_matches_returns_no_groups_rather_than_a_group_of_zero() {
        var nobody = new DashboardFilters(new CountryCode("JP"), null, new JobTitle(OURS), null, USD);

        var byDepartment = breakdowns.by(BreakdownDimension.DEPARTMENT, nobody);

        // No rows is the truthful answer: there is no department to name. The KPI cards say zero
        // because they are always four cards; a breakdown with an invented "0" row would be
        // asserting a group exists.
        assertThat(byDepartment.rows()).isEmpty();
        assertThat(byDepartment.dimension()).isEqualTo(BreakdownDimension.DEPARTMENT);
        assertThat(byDepartment.ratesAsOf())
                .as("an empty breakdown still says which day it would have converted through")
                .isEqualTo(java.time.LocalDate.of(2026, 9, 1));
    }

    @Test
    void the_breakdown_says_which_days_rates_it_used() {
        assertThat(breakdowns.by(BreakdownDimension.COUNTRY, ours()).ratesAsOf())
                .isEqualTo(java.time.LocalDate.of(2026, 9, 1));
    }

    private PayrollBreakdown.BreakdownRow groupNamed(PayrollBreakdown breakdown, String group) {
        return breakdown.rows().stream()
                .filter(row -> row.group().equals(group))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no group named " + group + " in " + breakdown.rows()));
    }

    private DashboardFilters ours() {
        // Pinned to a job title nothing else generates: the seeded org shares this table.
        return new DashboardFilters(null, null, new JobTitle(OURS), null, USD);
    }

    private void insert(String country, String salary, String department, String level) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO employee (id, employee_number, given_name, family_name, email, country_code,
                                      department, job_title, seniority_level, hire_date, status,
                                      salary_amount, salary_currency)
                VALUES (?, ?, 'Asha', ?, ?, ?, ?, ?, ?, DATE '2024-04-01', 'ACTIVE', ?, ?)
                """,
                id,
                "BRK-" + id,
                OURS,
                id + "@acme.example",
                country,
                department,
                OURS,
                level,
                new BigDecimal(salary),
                new CountryCode(country).currency().code());
    }

    static {
        Locale.setDefault(Locale.ROOT);
    }
}
