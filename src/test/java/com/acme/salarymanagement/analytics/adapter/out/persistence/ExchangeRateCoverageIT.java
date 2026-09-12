package com.acme.salarymanagement.analytics.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

import com.acme.salarymanagement.analytics.application.port.in.BreakdownDimension;
import com.acme.salarymanagement.analytics.application.port.in.DashboardFilters;
import com.acme.salarymanagement.analytics.application.port.in.DistributionDimension;
import com.acme.salarymanagement.analytics.application.port.in.GetPayrollBreakdown;
import com.acme.salarymanagement.analytics.application.port.in.GetPayrollSummary;
import com.acme.salarymanagement.analytics.application.port.in.GetSalaryDistribution;
import com.acme.salarymanagement.analytics.domain.UnconvertibleSalaries;
import com.acme.salarymanagement.shared.CountryCode;
import com.acme.salarymanagement.shared.CurrencyCode;
import com.acme.salarymanagement.shared.Department;
import com.acme.salarymanagement.shared.JobTitle;
import com.acme.salarymanagement.support.DatabaseOwner;
import com.acme.salarymanagement.support.PostgresIntegrationTest;

/**
 * What the dashboard does when it cannot convert a salary.
 *
 * <p>The answer has to be "refuse", not "assume 1.0". A rate of 1.0 for a currency with no rate row
 * turned 1,000,000 INR into 1,000,000 EUR and reported India as the organisation's largest payroll
 * cost at EUR 4.93bn, with a `ratesAsOf` of null and no error anywhere. A wrong payroll figure that
 * looks plausible is the worst thing this product can produce.
 */
@Transactional
@WithMockUser(roles = "HR_ANALYST")
class ExchangeRateCoverageIT extends PostgresIntegrationTest {

    private static final String OURS = "Zzzfxcoverage";
    private static final CurrencyCode USD = new CurrencyCode("USD");
    private static final CurrencyCode EUR = new CurrencyCode("EUR");

    @Autowired
    private GetPayrollSummary summaries;

    @Autowired
    private GetPayrollBreakdown breakdowns;

    @Autowired
    private GetSalaryDistribution distributions;

    @Autowired
    private JdbcTemplate jdbc;

    /**
     * The container is reused between runs and Flyway will not undo a committed insert, so a row
     * leaked by a JVM that died mid-test would make every dashboard test fail on the default path
     * with nothing in their own sources to explain it. Sweep before, not only after.
     */
    @org.junit.jupiter.api.BeforeEach
    @org.junit.jupiter.api.AfterEach
    void noRateRowOutlivesThisClass() throws Exception {
        try (java.sql.Connection owner = DatabaseOwner.connect();
                var sweep = owner.prepareStatement("DELETE FROM exchange_rate WHERE as_of <> DATE '2026-09-01'")) {
            sweep.executeUpdate();
        }
    }

    @Test
    void a_reporting_currency_the_table_cannot_reach_is_refused_rather_than_guessed() {
        insert("IN", "1000000.00");

        // The seeded table holds rates TO USD only. Reporting in EUR would need INR->EUR, which
        // does not exist - and the old answer was to multiply by 1 and call 1,000,000 rupees
        // 1,000,000 euros.
        assertThatThrownBy(() -> summaries.of(ours(EUR)))
                .isInstanceOf(UnconvertibleSalaries.class)
                .hasMessageContaining("EUR")
                .hasMessageContaining("No exchange rates")
                // The currencies are the actionable half, and are named even with no date to name.
                .hasMessageContaining("INR");
    }

    @Test
    void the_breakdown_refuses_the_same_query_the_cards_refuse() {
        insert("IN", "1000000.00");

        assertThatThrownBy(() -> breakdowns.by(BreakdownDimension.COUNTRY, ours(EUR)))
                .isInstanceOf(UnconvertibleSalaries.class);
    }

    @Test
    void the_distribution_refuses_the_same_query_the_cards_refuse() {
        insert("IN", "1000000.00");

        assertThatThrownBy(() -> distributions.by(DistributionDimension.JOB_TITLE, ours(EUR)))
                .isInstanceOf(UnconvertibleSalaries.class);
    }

    @Test
    void a_salary_already_in_the_reporting_currency_needs_no_rate_row() {
        insert("US", "100000.00");

        // There is no USD->USD row and there cannot be one: the migration's CHECK forbids a rate
        // from a currency to itself. Identity is a case, not a missing rate.
        assertThat(summaries.of(ours(USD)).totalSpend().amount()).isEqualByComparingTo("100000.00");
    }

    @Test
    void a_partially_refreshed_rate_table_is_refused_rather_than_converted_at_one() throws Exception {
        insert("IN", "1000000.00");
        insert("DE", "100000.00");

        // V5's comment is explicit that the dashboard picks ONE date and normalises everything
        // through it, so that this week's total and next week's are comparable statements rather
        // than coincidences. A feed that refreshes only the currencies that moved breaks that
        // premise: the snapshot date advances and the currencies that did not move have no row on
        // it. Silently, that made rupees worth dollars and reported global payroll as 6.19bn
        // instead of 1.21bn. The snapshot must be complete or refused - never partially applied.
        withRateRow("EUR", "USD", "1.09000000", "2026-10-01", () -> assertThatThrownBy(() -> summaries.of(ours(USD)))
                .isInstanceOf(UnconvertibleSalaries.class)
                .hasMessageContaining("INR")
                .hasMessageContaining("2026-10-01"));
    }

    /**
     * Adds a rate row as the database owner and removes it afterwards, whatever happens.
     *
     * <p>The application role holds SELECT only on {@code exchange_rate} (D103), and this row would
     * change the snapshot date every other test reads - so it is committed on its own connection
     * and deleted in a finally.
     */
    private void withRateRow(String from, String to, String rate, String asOf, Runnable assertions) throws Exception {
        try (java.sql.Connection owner = DatabaseOwner.connect()) {
            try (var insert = owner.prepareStatement(
                    "INSERT INTO exchange_rate (from_currency, to_currency, rate, as_of) VALUES (?, ?, ?, ?::date)")) {
                insert.setString(1, from);
                insert.setString(2, to);
                insert.setBigDecimal(3, new BigDecimal(rate));
                insert.setString(4, asOf);
                insert.executeUpdate();
            }
            try {
                assertions.run();
            } finally {
                try (var delete = owner.prepareStatement(
                        "DELETE FROM exchange_rate WHERE from_currency = ? AND to_currency = ? AND as_of = ?::date")) {
                    delete.setString(1, from);
                    delete.setString(2, to);
                    delete.setString(3, asOf);
                    delete.executeUpdate();
                }
            }
        }
    }

    private DashboardFilters ours(CurrencyCode reportIn) {
        return new DashboardFilters(null, null, new JobTitle(OURS), null, reportIn);
    }

    private void insert(String country, String salary) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO employee (id, employee_number, given_name, family_name, email, country_code,
                                      department, job_title, seniority_level, hire_date, status,
                                      salary_amount, salary_currency)
                VALUES (?, ?, 'Asha', ?, ?, ?, ?, ?, 'SENIOR', DATE '2024-04-01', 'ACTIVE', ?, ?)
                """,
                id,
                "FX-" + id,
                OURS,
                id + "@acme.example",
                country,
                new Department("Zzzfx").value(),
                OURS,
                new BigDecimal(salary),
                new CountryCode(country).currency().code());
    }

    static {
        Locale.setDefault(Locale.ROOT);
    }
}
