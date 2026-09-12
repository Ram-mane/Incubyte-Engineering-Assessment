package com.acme.salarymanagement.analytics.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

import com.acme.salarymanagement.analytics.application.port.in.DashboardFilters;
import com.acme.salarymanagement.analytics.application.port.in.GetPayrollSummary;
import com.acme.salarymanagement.shared.CountryCode;
import com.acme.salarymanagement.shared.CurrencyCode;
import com.acme.salarymanagement.shared.Department;
import com.acme.salarymanagement.support.CountingDataSource;
import com.acme.salarymanagement.support.PostgresIntegrationTest;

/**
 * The four KPI cards, against a known org in three currencies.
 *
 * <p>The figures are small and hand-computable on purpose. A dashboard test against ten thousand
 * generated rows can only assert that the number did not change; against nine people whose salaries
 * and rates are written down here, it asserts that the number is right.
 */
@Transactional
@WithMockUser(roles = "HR_ANALYST")
class PayrollSummaryIT extends PostgresIntegrationTest {

    private static final String OURS = "Zzzkpi";
    private static final CurrencyCode USD = new CurrencyCode("USD");

    @Autowired
    private GetPayrollSummary summaries;

    @Autowired
    private JdbcTemplate jdbc;

    /**
     * Nine people. Three in the US on 100,000 USD; three in Germany on 100,000 EUR; three in India
     * on 1,000,000 INR. With the seeded rates that is 100,000 + 108,500 + 11,900 USD each.
     */
    @BeforeEach
    void anOrgInThreeCurrencies() {
        // No cleanup line: the application role has no DELETE on employee, by design (D100).
        // This test is transactional, and it pins its assertions to a job title nothing else uses.
        insert("US", "100000.00", "Engineering");
        insert("US", "100000.00", "Engineering");
        insert("US", "100000.00", "Finance");
        insert("DE", "100000.00", "Engineering");
        insert("DE", "100000.00", "Engineering");
        insert("DE", "100000.00", "Finance");
        insert("IN", "1000000.00", "Engineering");
        insert("IN", "1000000.00", "Engineering");
        insert("IN", "1000000.00", "Finance");
    }

    @Test
    void the_four_cards_come_back_in_one_round_trip() {
        long statements = CountingDataSource.statementsIssuedBy(() -> summaries.of(ours()));

        // Four cards from four queries look fine in a demo and stutter on every filter change -
        // and worse, four queries see four slightly different databases.
        assertThat(statements)
                .as("headcount, total, average and median are one question")
                .isEqualTo(1);
    }

    @Test
    void every_currency_is_normalised_through_the_rate_table() {
        var summary = summaries.of(ours());

        // 3 x 100,000 USD + 3 x 108,500 USD + 3 x 11,900 USD
        assertThat(summary.headcount()).isEqualTo(9);
        assertThat(summary.totalSpend().amount()).isEqualByComparingTo("661200.00");
        assertThat(summary.totalSpend().currency()).isEqualTo(USD);
    }

    @Test
    void the_average_is_of_the_normalised_amounts_not_of_the_local_ones() {
        // Averaging local figures would put a rupee salary and a dollar salary in the same mean,
        // which is the mistake this whole FX table exists to prevent: 661,200 / 9 = 73,466.67.
        assertThat(summaries.of(ours()).averageSalary().amount()).isEqualByComparingTo("73466.67");
    }

    @Test
    void the_median_is_a_salary_somebody_actually_earns() {
        var median = summaries.of(ours()).medianSalary();

        // Nine people, three each at 11,900 / 100,000 / 108,500 USD. The middle one is 100,000 -
        // an amount three people are genuinely paid. percentile_cont would interpolate and return
        // a double, which is a float in a payroll figure.
        assertThat(median.amount()).isEqualByComparingTo("100000.00");
        assertThat(median.amount().toPlainString())
                .as("no float artefact: percentile_disc stays in numeric")
                .doesNotContain("0000000");
    }

    @Test
    void a_filter_narrows_the_cards_together() {
        var engineering = summaries.of(new DashboardFilters(
                null, new Department("Engineering"), null, null, DashboardFilters.DEFAULT_REPORTING_CURRENCY));

        // Six of the nine, two per market: 2 x (100,000 + 108,500 + 11,900) = 440,800.
        assertThat(engineering.headcount()).isEqualTo(6);
        assertThat(engineering.totalSpend().amount()).isEqualByComparingTo("440800.00");
    }

    @Test
    void a_country_filter_leaves_that_market_in_its_own_converted_figures() {
        var india = summaries.of(new DashboardFilters(
                new CountryCode("IN"), null, null, null, DashboardFilters.DEFAULT_REPORTING_CURRENCY));

        assertThat(india.headcount()).isEqualTo(3);
        assertThat(india.totalSpend().amount()).isEqualByComparingTo("35700.00");
    }

    @Test
    void the_summary_says_which_days_rates_it_used() {
        // A total normalised through "whatever rate was current" is a number nobody can reproduce.
        assertThat(summaries.of(ours()).ratesAsOf()).isEqualTo(java.time.LocalDate.of(2026, 9, 1));
    }

    private DashboardFilters ours() {
        // The seeded org is in the same table, so these assertions pin themselves to this test's
        // nine people through a job title nothing else uses.
        return new DashboardFilters(null, null, new com.acme.salarymanagement.shared.JobTitle(OURS), null, USD);
    }

    private void insert(String country, String salary, String department) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO employee (id, employee_number, given_name, family_name, email, country_code,
                                      department, job_title, seniority_level, hire_date, status,
                                      salary_amount, salary_currency)
                VALUES (?, ?, 'Asha', ?, ?, ?, ?, ?, 'SENIOR', DATE '2024-04-01', 'ACTIVE', ?, ?)
                """,
                id,
                "KPI-" + id,
                OURS,
                id + "@acme.example",
                country,
                department,
                OURS,
                new BigDecimal(salary),
                new CountryCode(country).currency().code());
    }

    @Test
    void an_empty_result_is_zero_rather_than_null() {
        var nobody = summaries.of(new DashboardFilters(
                new CountryCode("JP"), null, null, null, DashboardFilters.DEFAULT_REPORTING_CURRENCY));

        // An empty filter must render as "0 employees, $0", not as a broken card.
        assertThat(nobody.headcount()).isZero();
        assertThat(List.of(nobody.totalSpend(), nobody.averageSalary(), nobody.medianSalary()))
                .allSatisfy(money -> assertThat(money.amount()).isEqualByComparingTo("0.00"));
    }

    static {
        Locale.setDefault(Locale.ROOT);
    }
}
