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

import com.acme.salarymanagement.analytics.application.port.in.DashboardFilters;
import com.acme.salarymanagement.analytics.application.port.in.DistributionDimension;
import com.acme.salarymanagement.analytics.application.port.in.GetSalaryDistribution;
import com.acme.salarymanagement.analytics.application.port.in.SalaryDistribution;
import com.acme.salarymanagement.shared.CountryCode;
import com.acme.salarymanagement.shared.CurrencyCode;
import com.acme.salarymanagement.shared.Department;
import com.acme.salarymanagement.shared.JobTitle;
import com.acme.salarymanagement.support.CountingDataSource;
import com.acme.salarymanagement.support.PostgresIntegrationTest;

/**
 * Where a role's salaries sit, against four people whose quartiles can be read off by hand.
 *
 * <p>Four US salaries of 100k, 200k, 300k and 400k. With {@code percentile_disc} over four values
 * the quartiles are the values themselves - 100k, 200k, 300k, 400k - which is the whole argument
 * for {@code _disc} over {@code _cont}: every figure is an amount somebody is actually paid.
 */
@Transactional
@WithMockUser(roles = "HR_ANALYST")
class SalaryDistributionIT extends PostgresIntegrationTest {

    private static final String OURS = "Zzzdistribution";
    private static final String OTHER_ROLE = "Zzzdistribution-other";
    /** This test's own department, so grouping by job title sees these six people and no others. */
    private static final String OUR_DEPARTMENT = "ZzzdistDept";

    private static final String OUR_OTHER_DEPARTMENT = "ZzzdistDept2";
    private static final CurrencyCode USD = new CurrencyCode("USD");

    @Autowired
    private GetSalaryDistribution distributions;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void fourSalariesInOneRole() {
        insert(OURS, "US", "100000.00", OUR_DEPARTMENT);
        insert(OURS, "US", "200000.00", OUR_DEPARTMENT);
        insert(OURS, "US", "300000.00", OUR_DEPARTMENT);
        insert(OURS, "US", "400000.00", OUR_DEPARTMENT);
        // A second role in the same department, so grouping by job title has two groups to keep
        // apart, and one person in a second department so grouping by department does too.
        insert(OTHER_ROLE, "US", "90000.00", OUR_DEPARTMENT);
        insert(OTHER_ROLE, "US", "110000.00", OUR_DEPARTMENT);
        insert(OURS, "US", "150000.00", OUR_OTHER_DEPARTMENT);
    }

    @Test
    void every_percentile_of_every_group_comes_back_in_one_query() {
        long statements =
                CountingDataSource.statementsIssuedBy(() -> distributions.by(DistributionDimension.JOB_TITLE, ours()));

        // Four percentiles across every group is one query, not four per group.
        assertThat(statements).isEqualTo(1);
    }

    @Test
    void the_quartiles_are_amounts_somebody_is_actually_paid() {
        var role = groupNamed(distributions.by(DistributionDimension.JOB_TITLE, ours()), OURS);

        // percentile_disc over 100k/200k/300k/400k returns the values themselves. percentile_cont
        // would interpolate and hand back a double - a float in a payroll figure.
        assertThat(role.p25().amount()).isEqualByComparingTo("100000.00");
        assertThat(role.median().amount()).isEqualByComparingTo("200000.00");
        assertThat(role.p75().amount()).isEqualByComparingTo("300000.00");
        assertThat(role.p90().amount()).isEqualByComparingTo("400000.00");
    }

    @Test
    void no_percentile_carries_a_float_artefact() {
        var role = groupNamed(distributions.by(DistributionDimension.JOB_TITLE, ours()), OURS);

        assertThat(role.p25().amount().toPlainString()).doesNotContain("00000000");
        assertThat(role.median().amount().toPlainString()).doesNotContain("00000000");
        assertThat(role.p75().amount().toPlainString()).doesNotContain("00000000");
        assertThat(role.p90().amount().toPlainString()).doesNotContain("00000000");
    }

    @Test
    void the_range_is_the_lowest_and_highest_in_the_group() {
        var role = groupNamed(distributions.by(DistributionDimension.JOB_TITLE, ours()), OURS);

        assertThat(role.lowest().amount()).isEqualByComparingTo("100000.00");
        assertThat(role.highest().amount()).isEqualByComparingTo("400000.00");
        assertThat(role.headcount()).isEqualTo(4);
    }

    @Test
    void each_group_is_distributed_on_its_own_rather_than_across_the_org() {
        var byRole = distributions.by(DistributionDimension.JOB_TITLE, ours());

        var other = groupNamed(byRole, OTHER_ROLE);

        // 90k and 110k, entirely separate from the four-person role beside it.
        assertThat(other.lowest().amount()).isEqualByComparingTo("90000.00");
        assertThat(other.highest().amount()).isEqualByComparingTo("110000.00");
        assertThat(other.median().amount()).isEqualByComparingTo("90000.00");
    }

    @Test
    void percentiles_are_computed_on_normalised_amounts_not_local_ones() {
        // One German on 100,000 EUR is 108,500 USD, which must sort above the 100,000 USD people
        // rather than beside them.
        insert(OURS, "DE", "100000.00", OUR_DEPARTMENT);

        var role = groupNamed(distributions.by(DistributionDimension.JOB_TITLE, ours()), OURS);

        assertThat(role.headcount()).isEqualTo(5);
        assertThat(role.median().amount())
                .as("five people: 100,000 / 108,500 / 200,000 / 300,000 / 400,000 - the middle is 200,000")
                .isEqualByComparingTo("200000.00");
    }

    @Test
    void grouping_by_department_asks_the_same_question_of_a_different_column() {
        // Pinned by job title this time, because the department pin would leave one group.
        var byDepartment = distributions.by(DistributionDimension.DEPARTMENT, filters(null, null, OURS));

        assertThat(byDepartment.rows())
                .extracting(SalaryDistribution.DistributionRow::group)
                .containsExactlyInAnyOrder(OUR_DEPARTMENT, OUR_OTHER_DEPARTMENT);
    }

    @Test
    void a_filter_narrows_the_distribution_to_the_groups_that_survive_it() {
        var oneRole = filters(null, OUR_DEPARTMENT, OTHER_ROLE);

        var byRole = distributions.by(DistributionDimension.JOB_TITLE, oneRole);

        assertThat(byRole.rows()).hasSize(1);
        assertThat(byRole.rows().get(0).group()).isEqualTo(OTHER_ROLE);
        assertThat(byRole.rows().get(0).headcount()).isEqualTo(2);
    }

    @Test
    void a_filter_nobody_matches_returns_no_groups_but_still_names_its_rates() {
        var nobody = filters("JP", OUR_DEPARTMENT, null);

        var byRole = distributions.by(DistributionDimension.JOB_TITLE, nobody);

        assertThat(byRole.rows()).isEmpty();
        assertThat(byRole.ratesAsOf()).isEqualTo(java.time.LocalDate.of(2026, 9, 1));
    }

    private SalaryDistribution.DistributionRow groupNamed(SalaryDistribution distribution, String group) {
        return distribution.rows().stream()
                .filter(row -> row.group().equals(group))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no group named " + group));
    }

    /** Pinned to this test's own department: the seeded org shares this table. */
    private DashboardFilters ours() {
        return filters(null, OUR_DEPARTMENT, null);
    }

    private DashboardFilters filters(String country, String department, String jobTitle) {
        return new DashboardFilters(
                country == null ? null : new CountryCode(country),
                department == null ? null : new Department(department),
                jobTitle == null ? null : new JobTitle(jobTitle),
                null,
                USD);
    }

    private void insert(String jobTitle, String country, String salary, String department) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO employee (id, employee_number, given_name, family_name, email, country_code,
                                      department, job_title, seniority_level, hire_date, status,
                                      salary_amount, salary_currency)
                VALUES (?, ?, 'Asha', ?, ?, ?, ?, ?, 'SENIOR', DATE '2024-04-01', 'ACTIVE', ?, ?)
                """,
                id,
                "DST-" + id,
                "Zzzdist",
                id + "@acme.example",
                country,
                department,
                jobTitle,
                new BigDecimal(salary),
                new CountryCode(country).currency().code());
    }

    static {
        Locale.setDefault(Locale.ROOT);
    }
}
