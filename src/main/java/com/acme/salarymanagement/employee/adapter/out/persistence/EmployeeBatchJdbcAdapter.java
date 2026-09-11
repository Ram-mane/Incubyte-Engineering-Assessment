package com.acme.salarymanagement.employee.adapter.out.persistence;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.acme.salarymanagement.employee.application.port.out.EmployeeWriteRepository;
import com.acme.salarymanagement.employee.domain.Employee;

/**
 * Bulk insert over the seed pool. JDBC batches, not Hibernate: ten thousand entities through a
 * persistence context would take minutes and demonstrate precisely the instinct 05-DATA-MODEL
 * argues against.
 */
@Repository
@Profile("seed")
class EmployeeBatchJdbcAdapter implements EmployeeWriteRepository {

    private static final int BATCH_SIZE = 1_000;

    private static final String INSERT =
            """
            INSERT INTO employee (id, employee_number, given_name, family_name, email, country_code,
                                  department, job_title, seniority_level, hire_date, status,
                                  salary_amount, salary_currency)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbc;

    EmployeeBatchJdbcAdapter(@Qualifier("seedJdbcTemplate") JdbcTemplate seedJdbcTemplate) {
        this.jdbc = seedJdbcTemplate;
    }

    @Override
    public void saveAll(List<Employee> employees) {
        for (int from = 0; from < employees.size(); from += BATCH_SIZE) {
            insert(employees.subList(from, Math.min(from + BATCH_SIZE, employees.size())));
        }
    }

    @Override
    public void deleteEveryone() {
        // The revision log references employees, and a seed is a fresh world rather than an
        // increment. Only the seed pool can do this: the application role has no DELETE anywhere.
        jdbc.execute("TRUNCATE TABLE salary_revision, employee");
    }

    private void insert(List<Employee> batch) {
        jdbc.batchUpdate(INSERT, new BatchPreparedStatementSetter() {

            @Override
            public void setValues(PreparedStatement statement, int index) throws SQLException {
                Employee employee = batch.get(index);
                statement.setObject(1, employee.id().value());
                statement.setString(2, employee.employeeNumber().value());
                statement.setString(3, employee.name().given());
                statement.setString(4, employee.name().family());
                statement.setString(5, employee.email().value());
                statement.setString(6, employee.country().code());
                statement.setString(7, employee.department().value());
                statement.setString(8, employee.jobTitle().value());
                statement.setString(9, employee.level().name());
                statement.setObject(10, employee.hireDate(), Types.DATE);
                statement.setString(11, employee.status().name());
                statement.setBigDecimal(12, employee.currentSalary().amount());
                statement.setString(13, employee.currentSalary().currency().code());
            }

            @Override
            public int getBatchSize() {
                return batch.size();
            }
        });
    }
}
