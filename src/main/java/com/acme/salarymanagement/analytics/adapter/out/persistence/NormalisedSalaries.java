package com.acme.salarymanagement.analytics.adapter.out.persistence;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import com.acme.salarymanagement.analytics.application.port.in.DashboardFilters;
import com.acme.salarymanagement.analytics.domain.UnconvertibleSalaries;

/**
 * The salary set every dashboard query starts from: active employees matching the filters, each
 * salary converted to the reporting currency through one date's rates.
 *
 * <p>Shared by the three analytics adapters because all three were carrying the same CTE, and the
 * copy was the problem: a defect in it had to be found and fixed three times.
 *
 * <p><b>A missing rate is NULL, never 1.</b> {@code COALESCE(fx.rate, 1)} conflated "this salary is
 * already in the reporting currency" with "we have no rate for this currency" and answered both
 * with 1.0. The first is identity and correct - {@code V5}'s CHECK forbids a self-rate row, so it
 * has to be a case rather than a lookup. The second silently counted 1,000,000 INR as 1,000,000 EUR
 * and reported India as the organisation's largest payroll cost. They are now separate: identity is
 * a {@code CASE}, and an absent rate leaves NULL, which {@code sum} would quietly skip - so every
 * query also returns the currencies it could not convert and refuses rather than under-reporting.
 *
 * <p><b>One date for the whole snapshot, or none.</b> {@code V5} says why: an aggregate computed
 * today and the same aggregate next week are comparable only if both used one date's rates. So the
 * date is {@code max(as_of)} for the reporting currency and every currency in the result must have
 * a row on it. A feed that refreshes only what moved advances that date and orphans the rest; that
 * is now a refusal naming the currencies, not a total six times too large.
 */
final class NormalisedSalaries {

    /**
     * {@code %s} is the extra select list - a grouping column, or empty. It is never request data:
     * every caller passes a literal chosen from a closed enum.
     */
    static final String CTE =
            """
            WITH snapshot AS (
                SELECT max(as_of) AS as_of FROM exchange_rate WHERE to_currency = :reportingCurrency
            ),
            rates AS (
                SELECT r.from_currency, r.rate
                FROM   exchange_rate r, snapshot s
                WHERE  r.to_currency = :reportingCurrency
                  AND  r.as_of = s.as_of
            ),
            normalised AS (
                SELECT %s
                       e.salary_currency AS currency,
                       CASE
                           WHEN e.salary_currency = :reportingCurrency THEN e.salary_amount
                           ELSE e.salary_amount * fx.rate
                       END AS reporting_amount
                FROM   employee e
                LEFT JOIN rates fx ON fx.from_currency = e.salary_currency
                WHERE  e.status = 'ACTIVE'
                  AND  (CAST(:country    AS text) IS NULL OR e.country_code    = :country)
                  AND  (CAST(:department AS text) IS NULL OR e.department      = :department)
                  AND  (CAST(:jobTitle   AS text) IS NULL OR e.job_title       = :jobTitle)
                  AND  (CAST(:level      AS text) IS NULL OR e.seniority_level = :level)
            )
            """;

    /**
     * The two columns every dashboard query must select: the date it converted through, and the
     * currencies it could not convert. Selected by all three adapters so that none of them can
     * quietly drop a salary - {@code sum} skips NULLs, so an unconverted salary would otherwise
     * leave no trace at all.
     */
    static final String SNAPSHOT_AND_COVERAGE =
            """
            (SELECT as_of FROM snapshot) AS rates_as_of,
                   array_agg(DISTINCT currency) FILTER (WHERE reporting_amount IS NULL) AS unconvertible""";

    /**
     * The snapshot date on its own, for the one case the grouped queries cannot answer: no groups
     * came back, so no row carries it (D143).
     */
    static final String SNAPSHOT_DATE_ONLY =
            "SELECT max(as_of) FROM exchange_rate WHERE to_currency = :reportingCurrency";

    private NormalisedSalaries() {}

    static MapSqlParameterSource parameters(DashboardFilters filters) {
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

    /** The currencies one row could not convert, read from the {@code unconvertible} column. */
    static Set<String> unconvertibleIn(ResultSet row) throws SQLException {
        Array currencies = row.getArray("unconvertible");
        if (currencies == null) {
            return Set.of();
        }
        Set<String> missing = new LinkedHashSet<>();
        for (Object currency : (Object[]) currencies.getArray()) {
            if (currency != null) {
                missing.add(currency.toString().trim());
            }
        }
        return missing;
    }

    /**
     * Refuses the whole answer if any salary in it could not be converted.
     *
     * <p>All or nothing on purpose: a total that quietly omits every rupee is a number an HR
     * manager would act on, and would have no way to question.
     */
    static void refuseIfIncomplete(Set<String> unconvertible, DashboardFilters filters, LocalDate asOf) {
        if (unconvertible.isEmpty()) {
            return;
        }
        String reportingCurrency = filters.reportingCurrency().code();
        if (asOf == null) {
            throw new UnconvertibleSalaries(reportingCurrency);
        }
        throw new UnconvertibleSalaries(reportingCurrency, asOf, List.copyOf(unconvertible));
    }
}
