package com.acme.salarymanagement.analytics.adapter.out.persistence;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.acme.salarymanagement.analytics.application.port.in.DashboardFilters;
import com.acme.salarymanagement.analytics.application.port.in.PayrollSummary;
import com.acme.salarymanagement.analytics.application.port.out.PayrollSummaryRepository;
import com.acme.salarymanagement.shared.Money;

/**
 * Four KPI cards, one round trip.
 *
 * <p>Four cards driven by four queries looks fine in a demo and is a visible stutter every time a
 * filter changes. More than that, four queries can disagree: run separately they see four slightly
 * different databases, and a median that does not belong to the headcount above it is worse than a
 * slow dashboard.
 *
 * <p><b>percentile_disc, not percentile_cont.</b> `05-DATA-MODEL.md` specified `percentile_cont`,
 * which in PostgreSQL returns <em>double precision</em> — it interpolates, so it has to leave
 * numeric. Against this data it returns {@code 113930.00993500001}: a float artefact in a payroll
 * figure, which `CLAUDE.md` forbids outright. `percentile_disc` returns `numeric` and returns an
 * amount somebody is actually paid, which for a median salary is the more truthful answer anyway.
 *
 * <p><b>Conversion happens per row, and ADR-0013 still holds.</b> That ADR prohibits per-amount
 * conversion because {@code Money.convertTo} rounds to the currency's scale on every call, so a
 * total built from rounded parts drifts. PostgreSQL's `numeric` multiplication is exact and nothing
 * rounds until the end: summing per currency and converting once produces
 * {@code 1213179157.200437000000}, and converting per row produces the same digits, drift exactly
 * zero. Measured, not assumed — and per-row is required regardless, because a median cannot be
 * computed from subtotals.
 */
@Repository
class PayrollSummaryJdbcAdapter implements PayrollSummaryRepository {

    private static final String SUMMARY = NormalisedSalaries.CTE.formatted("")
            + """
            SELECT count(*)                                                      AS headcount,
                   COALESCE(sum(reporting_amount), 0)                            AS total_spend,
                   COALESCE(avg(reporting_amount), 0)                            AS average_salary,
                   COALESCE(percentile_disc(0.5) WITHIN GROUP (ORDER BY reporting_amount), 0)
                                                                                 AS median_salary,
                   %s
            FROM   normalised
            """
                    .formatted(NormalisedSalaries.SNAPSHOT_AND_COVERAGE);

    private final NamedParameterJdbcTemplate jdbc;

    PayrollSummaryJdbcAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public PayrollSummary summarise(DashboardFilters filters) {
        return jdbc.queryForObject(
                SUMMARY,
                NormalisedSalaries.parameters(filters),
                (ResultSet row, int number) -> asSummary(row, filters));
    }

    private static PayrollSummary asSummary(ResultSet row, DashboardFilters filters) throws SQLException {
        LocalDate ratesAsOf = row.getObject("rates_as_of", LocalDate.class);
        NormalisedSalaries.refuseIfIncomplete(NormalisedSalaries.unconvertibleIn(row), filters, ratesAsOf);
        return new PayrollSummary(
                row.getLong("headcount"),
                money(row.getBigDecimal("total_spend"), filters),
                money(row.getBigDecimal("average_salary"), filters),
                money(row.getBigDecimal("median_salary"), filters),
                ratesAsOf);
    }

    /** {@code Money} rounds to the currency's scale once, here, at the edge of the query. */
    private static Money money(BigDecimal amount, DashboardFilters filters) {
        return Money.of(amount == null ? BigDecimal.ZERO : amount, filters.reportingCurrency());
    }
}
