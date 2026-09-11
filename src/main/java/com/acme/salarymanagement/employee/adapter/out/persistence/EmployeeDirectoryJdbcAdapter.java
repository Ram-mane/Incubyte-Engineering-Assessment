package com.acme.salarymanagement.employee.adapter.out.persistence;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.acme.salarymanagement.employee.application.port.out.EmployeeDirectoryRepository;
import com.acme.salarymanagement.employee.application.port.out.EmployeeSummary;
import com.acme.salarymanagement.shared.CountryCode;
import com.acme.salarymanagement.shared.CurrencyCode;
import com.acme.salarymanagement.shared.Department;
import com.acme.salarymanagement.shared.JobTitle;
import com.acme.salarymanagement.shared.Money;
import com.acme.salarymanagement.shared.SeniorityLevel;

/**
 * The directory read, as one statement returning exactly the columns the screen shows.
 *
 * <p>No JPA here on purpose: there is no aggregate to load and nothing to dirty-check, and an
 * entity would turn a flat projection into an object graph for no one's benefit. Ordering is
 * {@code family_name, given_name, id} so that paging is stable - two people called Rao must not
 * swap places between page one and page two, and a total order needs a unique tiebreaker.
 *
 * <p>OFFSET, for now. It reads and discards every row it skips, so it degrades with depth and can
 * skip or repeat rows when the data changes between pages; keyset pagination replaces it at 2.6
 * (ADR-0004). This is the walking skeleton of the directory, not its final query.
 */
@Repository
class EmployeeDirectoryJdbcAdapter implements EmployeeDirectoryRepository {

    private static final String PAGE =
            """
            SELECT id, employee_number, given_name, family_name, email, country_code,
                   department, job_title, seniority_level, salary_amount, salary_currency
            FROM   employee
            ORDER BY family_name, given_name, id
            LIMIT ? OFFSET ?
            """;

    private static final RowMapper<EmployeeSummary> AS_SUMMARY = (row, number) -> new EmployeeSummary(
            row.getObject("id", java.util.UUID.class),
            row.getString("employee_number"),
            row.getString("given_name"),
            row.getString("family_name"),
            row.getString("email"),
            new CountryCode(row.getString("country_code")),
            new Department(row.getString("department")),
            new JobTitle(row.getString("job_title")),
            SeniorityLevel.valueOf(row.getString("seniority_level")),
            Money.of(row.getBigDecimal("salary_amount"), new CurrencyCode(row.getString("salary_currency"))));

    private final JdbcTemplate jdbc;

    EmployeeDirectoryJdbcAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<EmployeeSummary> findPage(long offset, int limit) {
        return jdbc.query(PAGE, AS_SUMMARY, limit, offset);
    }

    @Override
    public long count() {
        Long total = jdbc.queryForObject("SELECT count(*) FROM employee", Long.class);
        return total == null ? 0 : total;
    }
}
