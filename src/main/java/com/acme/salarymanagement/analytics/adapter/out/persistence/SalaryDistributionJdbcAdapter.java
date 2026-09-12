package com.acme.salarymanagement.analytics.adapter.out.persistence;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.acme.salarymanagement.analytics.application.port.in.DashboardFilters;
import com.acme.salarymanagement.analytics.application.port.in.DistributionDimension;
import com.acme.salarymanagement.analytics.application.port.in.SalaryDistribution;
import com.acme.salarymanagement.analytics.application.port.out.SalaryDistributionRepository;
import com.acme.salarymanagement.shared.Money;

/**
 * Four percentiles and a range for every group, in one query.
 *
 * <p><b>percentile_disc throughout, never percentile_cont.</b> {@code _cont} interpolates, so
 * PostgreSQL has to leave {@code numeric} and returns {@code double precision} - a float in a
 * payroll figure, which this codebase forbids outright. {@code _disc} returns an amount somebody in
 * the group is genuinely paid, which for a quartile of salaries is also the more useful answer: a
 * p75 of 137,499.9987 is not a number anybody can act on (D135).
 *
 * <p>The grouping column is chosen from a closed enum, never interpolated from input - see
 * {@link PayrollBreakdownJdbcAdapter} for why that is the only safe way to vary a GROUP BY.
 *
 * <p>Ordered by the widest spread first, because the reason to read this table at all is to find
 * the roles where pay is least consistent. Group name is the tiebreak, so the order is stable
 * between filter changes.
 */
@Repository
class SalaryDistributionJdbcAdapter implements SalaryDistributionRepository {

    private static final String DISTRIBUTION = NormalisedSalaries.CTE_PLACEHOLDER
            + """
            SELECT grp,
                   count(*)              AS headcount,
                   min(reporting_amount) AS lowest,
                   percentile_disc(0.25) WITHIN GROUP (ORDER BY reporting_amount) AS p25,
                   percentile_disc(0.50) WITHIN GROUP (ORDER BY reporting_amount) AS median,
                   percentile_disc(0.75) WITHIN GROUP (ORDER BY reporting_amount) AS p75,
                   percentile_disc(0.90) WITHIN GROUP (ORDER BY reporting_amount) AS p90,
                   max(reporting_amount) AS highest,
                   %s
            FROM   normalised
            GROUP BY grp
            ORDER BY max(reporting_amount) - min(reporting_amount) DESC, grp
            """
                    .formatted(NormalisedSalaries.SNAPSHOT_AND_COVERAGE);

    private final NamedParameterJdbcTemplate jdbc;

    SalaryDistributionJdbcAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public SalaryDistribution distribute(DistributionDimension dimension, DashboardFilters filters) {
        MapSqlParameterSource parameters = NormalisedSalaries.parameters(filters);
        String sql = DISTRIBUTION.replace(NormalisedSalaries.GROUPING, "e." + columnFor(dimension) + " AS grp,");

        Set<String> unconvertible = new LinkedHashSet<>();
        AtomicReference<LocalDate> asOf = new AtomicReference<>();
        List<SalaryDistribution.DistributionRow> rows = jdbc.query(sql, parameters, (ResultSet row, int number) -> {
            unconvertible.addAll(NormalisedSalaries.unconvertibleIn(row));
            asOf.compareAndSet(null, row.getObject("rates_as_of", LocalDate.class));
            return asRow(row, filters);
        });
        NormalisedSalaries.refuseIfIncomplete(unconvertible, filters, asOf.get());

        LocalDate ratesAsOf = rows.isEmpty()
                ? jdbc.queryForObject(NormalisedSalaries.SNAPSHOT_DATE_ONLY, parameters, LocalDate.class)
                : asOf.get();
        return new SalaryDistribution(dimension, rows, ratesAsOf);
    }

    /** Exhaustive over the enum, so adding a dimension without deciding its column will not compile. */
    private static String columnFor(DistributionDimension dimension) {
        return switch (dimension) {
            case JOB_TITLE -> "job_title";
            case DEPARTMENT -> "department";
        };
    }

    private static SalaryDistribution.DistributionRow asRow(ResultSet row, DashboardFilters filters)
            throws SQLException {
        return new SalaryDistribution.DistributionRow(
                row.getString("grp"),
                row.getLong("headcount"),
                money(row.getBigDecimal("lowest"), filters),
                money(row.getBigDecimal("p25"), filters),
                money(row.getBigDecimal("median"), filters),
                money(row.getBigDecimal("p75"), filters),
                money(row.getBigDecimal("p90"), filters),
                money(row.getBigDecimal("highest"), filters));
    }

    /** {@code Money} rounds to the currency's scale once, here, at the edge of the query. */
    private static Money money(BigDecimal amount, DashboardFilters filters) {
        return Money.of(amount == null ? BigDecimal.ZERO : amount, filters.reportingCurrency());
    }
}
