package com.acme.salarymanagement.analytics.adapter.out.persistence;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

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

    private static final String BREAKDOWN =
            """
            WITH rates AS (
                SELECT from_currency, rate
                FROM   exchange_rate
                WHERE  to_currency = :reportingCurrency
                  AND  as_of = (SELECT max(as_of) FROM exchange_rate WHERE to_currency = :reportingCurrency)
            ),
            normalised AS (
                SELECT e.%s                                    AS grp,
                       e.salary_amount * COALESCE(fx.rate, 1)  AS reporting_amount
                FROM   employee e
                LEFT JOIN rates fx ON fx.from_currency = e.salary_currency
                WHERE  e.status = 'ACTIVE'
                  AND  (CAST(:country    AS text) IS NULL OR e.country_code    = :country)
                  AND  (CAST(:department AS text) IS NULL OR e.department      = :department)
                  AND  (CAST(:jobTitle   AS text) IS NULL OR e.job_title       = :jobTitle)
                  AND  (CAST(:level      AS text) IS NULL OR e.seniority_level = :level)
            )
            SELECT grp,
                   count(*)                AS headcount,
                   sum(reporting_amount)   AS total_spend,
                   avg(reporting_amount)   AS average_salary
            FROM   normalised
            GROUP BY grp
            ORDER BY total_spend DESC, grp
            """;

    private static final String RATES_AS_OF =
            "SELECT max(as_of) FROM exchange_rate WHERE to_currency = :reportingCurrency";

    private final NamedParameterJdbcTemplate jdbc;

    PayrollBreakdownJdbcAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public PayrollBreakdown breakDown(BreakdownDimension dimension, DashboardFilters filters) {
        MapSqlParameterSource parameters = parameters(filters);
        String sql = BREAKDOWN.formatted(columnFor(dimension));

        List<PayrollBreakdown.BreakdownRow> rows =
                jdbc.query(sql, parameters, (ResultSet row, int number) -> asRow(row, filters));

        return new PayrollBreakdown(dimension, rows, ratesAsOf(parameters));
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

    private MapSqlParameterSource parameters(DashboardFilters filters) {
        return new MapSqlParameterSource()
                .addValue("reportingCurrency", filters.reportingCurrency().code())
                .addValue(
                        "country",
                        filters.country() == null ? null : filters.country().code())
                .addValue(
                        "department",
                        filters.department() == null
                                ? null
                                : filters.department().value())
                .addValue(
                        "jobTitle",
                        filters.jobTitle() == null ? null : filters.jobTitle().value())
                .addValue(
                        "level",
                        filters.level() == null ? null : filters.level().name());
    }

    /**
     * Read separately rather than joined onto every group, because it is one date for the whole
     * answer. It is the second statement of two, which is what the round-trip test allows.
     */
    private LocalDate ratesAsOf(MapSqlParameterSource parameters) {
        return jdbc.queryForObject(RATES_AS_OF, parameters, LocalDate.class);
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
