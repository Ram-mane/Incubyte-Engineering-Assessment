package com.acme.salarymanagement.employee.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.acme.salarymanagement.employee.application.port.out.EmployeeRepository;
import com.acme.salarymanagement.employee.domain.EmailAddress;
import com.acme.salarymanagement.employee.domain.Employee;
import com.acme.salarymanagement.employee.domain.EmployeeId;
import com.acme.salarymanagement.employee.domain.EmployeeNumber;
import com.acme.salarymanagement.employee.domain.EmploymentStatus;
import com.acme.salarymanagement.employee.domain.PersonName;
import com.acme.salarymanagement.shared.CountryCode;
import com.acme.salarymanagement.shared.CurrencyCode;
import com.acme.salarymanagement.shared.Department;
import com.acme.salarymanagement.shared.JobTitle;
import com.acme.salarymanagement.shared.Money;
import com.acme.salarymanagement.shared.SeniorityLevel;

/** The aggregate, loaded whole and written back where it changes. */
@Repository
class EmployeeJdbcAdapter implements EmployeeRepository {

    /** One employee, one row. Anything else means the id matched nobody, or the schema changed. */
    private static final int ONE_ROW = 1;

    private static final RowMapper<Employee> AS_EMPLOYEE = (row, number) -> new Employee(
            new EmployeeId(row.getObject("id", UUID.class)),
            new EmployeeNumber(row.getString("employee_number")),
            new PersonName(row.getString("given_name"), row.getString("family_name")),
            new EmailAddress(row.getString("email")),
            new CountryCode(row.getString("country_code")),
            new Department(row.getString("department")),
            new JobTitle(row.getString("job_title")),
            SeniorityLevel.valueOf(row.getString("seniority_level")),
            row.getObject("hire_date", java.time.LocalDate.class),
            EmploymentStatus.valueOf(row.getString("status")),
            Money.of(row.getBigDecimal("salary_amount"), new CurrencyCode(row.getString("salary_currency"))));

    private final JdbcTemplate jdbc;

    EmployeeJdbcAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Employee> load(EmployeeId id) {
        return jdbc
                .query(
                        """
                        SELECT id, employee_number, given_name, family_name, email, country_code,
                               department, job_title, seniority_level, hire_date, status,
                               salary_amount, salary_currency
                        FROM   employee
                        WHERE  id = ?
                        """,
                        AS_EMPLOYEE,
                        id.value())
                .stream()
                .findFirst();
    }

    @Override
    public void saveCurrentSalaryOf(Employee employee) {
        int updated = jdbc.update(
                "UPDATE employee SET salary_amount = ?, salary_currency = ? WHERE id = ?",
                employee.currentSalary().amount(),
                employee.currentSalary().currency().code(),
                employee.id().value());
        if (updated != ONE_ROW) {
            // Silence here would mean pay that did not move while a revision said it had.
            throw new IllegalStateException(
                    "employee %s was not updated".formatted(employee.id().value()));
        }
    }
}
