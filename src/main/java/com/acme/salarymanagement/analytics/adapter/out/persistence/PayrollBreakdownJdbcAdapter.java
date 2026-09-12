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

import com.acme.salarymanagement.analytics.application.port.in.BreakdownDimension;
import com.acme.salarymanagement.analytics.application.port.in.DashboardFilters;
import com.acme.salarymanagement.analytics.application.port.in.PayrollBreakdown;
import com.acme.salarymanagement.analytics.application.port.out.PayrollBreakdownRepository;
import com.acme.salarymanagement.shared.Money;

/**
 * Every group in one query, ordered by spend.
 *
 * <p><b>The grouping column is chosen, never interpolated.</b> SQL cannot bind an identifier, so
 * the column has to be written into the statement — and this is the only place in the codebase
 * where that is true. The switch below is exhaustive over a closed enum, so the set of strings that
 * can reach {@code GROUP BY} is the three literals written here; nothing from a request can widen
 * it. Every value is still bound.
 *
 * <p>Ordered by spend descending, then by group name: without a deterministic tiebreak two groups
 * on identical totals swap places between filter changes, and a chart that reorders itself for no
 * visible reason reads as a bug in the data.
 */
@Repository
class PayrollBreakdownJdbcAdapter implements PayrollBreakdownRepository {

    private static final String BREAKDOWN = NormalisedSalaries.CTE_PLACEHOLDER
            + """
            SELECT grp,
                   count(*)                AS headcount,
                   sum(reporting_amount)   AS total_spend,
                   avg(reporting_amount)   AS average_salary,
                   %s
            FROM   normalised
            GROUP BY grp
            ORDER BY total_spend DESC, grp
            """
                    .formatted(NormalisedSalaries.SNAPSHOT_AND_COVERAGE);

    private final NamedParameterJdbcTemplate jdbc;

    PayrollBreakdownJdbcAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public PayrollBreakdown breakDown(BreakdownDimension dimension, DashboardFilters filters) {
        MapSqlParameterSource parameters = NormalisedSalaries.parameters(filters);
        String sql = BREAKDOWN.replace(NormalisedSalaries.GROUPING, "e." + columnFor(dimension) + " AS grp,");

        Set<String> unconvertible = new LinkedHashSet<>();
        AtomicReference<LocalDate> asOf = new AtomicReference<>();
        List<PayrollBreakdown.BreakdownRow> rows = jdbc.query(sql, parameters, (ResultSet row, int number) -> {
            unconvertible.addAll(NormalisedSalaries.unconvertibleIn(row));
            asOf.compareAndSet(null, row.getObject("rates_as_of", LocalDate.class));
            return asRow(row, filters);
        });
        NormalisedSalaries.refuseIfIncomplete(unconvertible, filters, asOf.get());

        // No group means no employee to convert, so the date still has to come from its own read.
        return new PayrollBreakdown(dimension, rows, rows.isEmpty() ? ratesAsOf(parameters) : asOf.get());
    }

    /**
     * The closed set of columns a breakdown may group by. Exhaustive over the enum, so adding a
     * dimension without deciding its column will not compile.
     */
    private static String columnFor(BreakdownDimension dimension) {
        return switch (dimension) {
            case DEPARTMENT -> "department";
            case COUNTRY -> "country_code";
            case LEVEL -> "seniority_level";
        };
    }

    /**
     * Only for the empty case. A grouped query that returns no groups cannot carry the snapshot
     * date, and a filter matching nobody still has to say which day it would have converted
     * through (D143). When there are groups the date rides along on every row, so the populated
     * path - which is every path a dashboard actually renders - is one statement.
     */
    private LocalDate ratesAsOf(MapSqlParameterSource parameters) {
        return jdbc.queryForObject(NormalisedSalaries.SNAPSHOT_DATE_ONLY, parameters, LocalDate.class);
    }

    private static PayrollBreakdown.BreakdownRow asRow(ResultSet row, DashboardFilters filters) throws SQLException {
        return new PayrollBreakdown.BreakdownRow(
                row.getString("grp"),
                row.getLong("headcount"),
                money(row.getBigDecimal("total_spend"), filters),
                money(row.getBigDecimal("average_salary"), filters));
    }

    /** {@code Money} rounds to the currency's scale once, here, at the edge of the query. */
    private static Money money(BigDecimal amount, DashboardFilters filters) {
        return Money.of(amount == null ? BigDecimal.ZERO : amount, filters.reportingCurrency());
    }
}
