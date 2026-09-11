package com.acme.salarymanagement.employee.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.acme.salarymanagement.employee.application.port.in.DirectoryCursor;
import com.acme.salarymanagement.employee.application.port.in.DirectoryFilters;
import com.acme.salarymanagement.employee.application.port.out.EmployeeDirectoryRepository;
import com.acme.salarymanagement.employee.application.port.out.EmployeeSummary;
import com.acme.salarymanagement.employee.application.port.out.FilterableColumn;
import com.acme.salarymanagement.shared.CountryCode;
import com.acme.salarymanagement.shared.CurrencyCode;
import com.acme.salarymanagement.shared.Department;
import com.acme.salarymanagement.shared.JobTitle;
import com.acme.salarymanagement.shared.Money;
import com.acme.salarymanagement.shared.SeniorityLevel;

/**
 * The directory read: one statement, filtered, searched and keyset-paged.
 *
 * <p>Keyset, not OFFSET. {@code OFFSET 9950} reads and discards nine thousand nine hundred and
 * fifty rows to return fifty, so the last page of ten thousand costs two hundred times the first;
 * and because it counts positions rather than naming a place, a row inserted while someone pages
 * shifts everything after it, silently repeating one row and skipping another. The row-value
 * comparison {@code (family_name, given_name, id) > (?, ?, ?)} names the place instead, reads
 * exactly the rows it returns, and is stable under concurrent writes (ADR-0004).
 *
 * <p>Every filter is optional in one statement rather than assembled by string concatenation:
 * {@code :country IS NULL OR country_code = :country}. PostgreSQL discards the unused branches
 * when it plans, and no user input is ever concatenated into SQL.
 */
@Repository
class EmployeeDirectoryJdbcAdapter implements EmployeeDirectoryRepository {

    private static final String MATCHING =
            """
            WHERE  (CAST(:country    AS text) IS NULL OR e.country_code    = :country)
              AND  (CAST(:department AS text) IS NULL OR e.department      = :department)
              AND  (CAST(:jobTitle   AS text) IS NULL OR e.job_title       = :jobTitle)
              AND  (CAST(:level      AS text) IS NULL OR e.seniority_level = :level)
              AND  (CAST(:search     AS text) IS NULL
                    OR (e.given_name || ' ' || e.family_name || ' ' || e.email) ILIKE :search)
            """;

    private static final String PAGE =
            """
            SELECT e.id, e.employee_number, e.given_name, e.family_name, e.email, e.country_code,
                   e.department, e.job_title, e.seniority_level, e.salary_amount, e.salary_currency
            FROM   employee e
            """
                    + MATCHING
                    + """
              AND  (CAST(:afterFamilyName AS text) IS NULL
                    OR (e.family_name, e.given_name, e.id)
                       > (:afterFamilyName, :afterGivenName, CAST(:afterId AS uuid)))
            ORDER BY e.family_name, e.given_name, e.id
            LIMIT :limit
            """;

    private static final String HOW_MANY = "SELECT count(*) FROM employee e\n" + MATCHING;

    private static final RowMapper<EmployeeSummary> AS_SUMMARY = (row, number) -> new EmployeeSummary(
            row.getObject("id", UUID.class),
            row.getString("employee_number"),
            row.getString("given_name"),
            row.getString("family_name"),
            row.getString("email"),
            new CountryCode(row.getString("country_code")),
            new Department(row.getString("department")),
            new JobTitle(row.getString("job_title")),
            SeniorityLevel.valueOf(row.getString("seniority_level")),
            Money.of(row.getBigDecimal("salary_amount"), new CurrencyCode(row.getString("salary_currency"))));

    private final NamedParameterJdbcTemplate jdbc;

    EmployeeDirectoryJdbcAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<EmployeeSummary> findPage(DirectoryFilters filters, String search, DirectoryCursor after, int limit) {
        MapSqlParameterSource parameters = matching(filters, search)
                .addValue("afterFamilyName", after == null ? null : after.familyName())
                .addValue("afterGivenName", after == null ? null : after.givenName())
                .addValue("afterId", after == null ? null : after.id().toString())
                .addValue("limit", limit);
        return jdbc.query(PAGE, parameters, AS_SUMMARY);
    }

    @Override
    public long count(DirectoryFilters filters, String search) {
        Long total = jdbc.queryForObject(HOW_MANY, matching(filters, search), Long.class);
        return total == null ? 0 : total;
    }

    @Override
    public List<String> distinctValuesOf(FilterableColumn column) {
        // The column name comes from an enum constant, never from the request: there is no path
        // by which anything a caller typed becomes part of this statement.
        String distinct =
                switch (column) {
                    case COUNTRY -> "SELECT DISTINCT country_code FROM employee ORDER BY 1";
                    case DEPARTMENT -> "SELECT DISTINCT department FROM employee ORDER BY 1";
                    case JOB_TITLE -> "SELECT DISTINCT job_title FROM employee ORDER BY 1";
                };
        return jdbc.getJdbcTemplate().queryForList(distinct, String.class);
    }

    private static MapSqlParameterSource matching(DirectoryFilters filters, String search) {
        DirectoryFilters applied = filters == null ? DirectoryFilters.none() : filters;
        return new MapSqlParameterSource()
                .addValue(
                        "country",
                        applied.country() == null ? null : applied.country().code())
                .addValue(
                        "department",
                        applied.department() == null
                                ? null
                                : applied.department().value())
                .addValue(
                        "jobTitle",
                        applied.jobTitle() == null ? null : applied.jobTitle().value())
                .addValue(
                        "level",
                        applied.level() == null ? null : applied.level().name())
                // The wildcards are part of the value, not of the statement: the search term is a
                // bound parameter and is never concatenated into SQL.
                .addValue("search", search == null || search.isBlank() ? null : "%" + search.trim() + "%");
    }
}
