package com.acme.salarymanagement.employee.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;

import com.acme.salarymanagement.employee.application.port.in.ChangeSalary;
import com.acme.salarymanagement.employee.application.port.in.ChangeSalaryCommand;
import com.acme.salarymanagement.employee.application.port.out.SalaryRevisionLog;
import com.acme.salarymanagement.employee.domain.ChangeReason;
import com.acme.salarymanagement.employee.domain.EmployeeId;
import com.acme.salarymanagement.employee.domain.SalaryRevision;
import com.acme.salarymanagement.employee.domain.UserId;
import com.acme.salarymanagement.shared.CurrencyCode;
import com.acme.salarymanagement.shared.Money;
import com.acme.salarymanagement.support.PostgresIntegrationTest;

/**
 * Pay moves and the log records it, or neither happens.
 *
 * <p>This is the project's headline invariant arriving at the layer where it can still be lost.
 * {@code Employee.changeSalaryTo} makes it impossible to change pay without <em>producing</em> a
 * revision; nothing below the aggregate makes it impossible to persist the salary and lose the
 * revision. A half-committed change is a salary nobody can account for - exactly what the missing
 * setter and the INSERT-only grant were built to prevent - and every happy-path test still passes.
 *
 * <p>So the log is replaced with one that issues an INSERT the database refuses, after the
 * employee UPDATE has already been issued. Not a thrown exception: a real failed statement, so
 * what is being tested is the database rolling back the write that came before it.
 *
 * <p>Deliberately not {@code @Transactional}. A test transaction would wrap the use case's own and
 * roll everything back regardless, which would make this pass no matter what the service did.
 */
@Import(ChangeSalaryIsAllOrNothingIT.ALogThatCannotWrite.class)
// Authorisation lives at the use case (docs/10-SECURITY.md), so a caller with no role
// reaches nothing. That is asserted separately; here it is a precondition.
@WithMockUser(roles = "HR_MANAGER")
class ChangeSalaryIsAllOrNothingIT extends PostgresIntegrationTest {

    private static final CurrencyCode INR = new CurrencyCode("INR");
    private static final Money ORIGINAL = Money.of("1200000.00", INR);

    @Autowired
    private ChangeSalary changeSalary;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID employeeId;
    private UUID actorId;

    @BeforeEach
    void anEmployeeOnTwelveLakh() {
        employeeId = UUID.randomUUID();
        actorId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO app_user (id, email, password_hash, role) VALUES (?, ?, 'not-a-real-hash', 'HR_MANAGER')",
                actorId,
                actorId + "@acme.example");
        jdbc.update(
                """
                INSERT INTO employee (id, employee_number, given_name, family_name, email, country_code,
                                      department, job_title, seniority_level, hire_date, status,
                                      salary_amount, salary_currency)
                VALUES (?, ?, 'Asha', 'Rao', ?, 'IN', 'Engineering', 'Engineer', 'SENIOR',
                        DATE '2024-04-01', 'ACTIVE', ?, 'INR')
                """,
                employeeId,
                "TX-" + employeeId,
                employeeId + "@acme.example",
                ORIGINAL.amount());
    }

    @Test
    void a_salary_does_not_move_when_its_revision_cannot_be_written() {
        assertThatThrownBy(() -> changeSalary.change(new ChangeSalaryCommand(
                        new EmployeeId(employeeId),
                        Money.of("1380000.00", INR),
                        ChangeReason.MERIT,
                        new UserId(actorId),
                        "Annual merit review")))
                .as("the write failed, and the use case must not swallow that")
                .isNotNull();

        assertThat(storedSalary())
                .as("pay that moved without an audit record is the one thing this system must not do")
                .isEqualByComparingTo(ORIGINAL.amount());
    }

    @Test
    void nothing_is_left_in_the_log_either() {
        assertThatThrownBy(() -> changeSalary.change(new ChangeSalaryCommand(
                        new EmployeeId(employeeId),
                        Money.of("1380000.00", INR),
                        ChangeReason.MERIT,
                        new UserId(actorId),
                        null)))
                .isNotNull();

        assertThat(revisionsFor(employeeId)).isZero();
    }

    /** Read after the transaction has ended, so this is committed state and not a dirty read. */
    private BigDecimal storedSalary() {
        return jdbc.queryForObject("SELECT salary_amount FROM employee WHERE id = ?", BigDecimal.class, employeeId);
    }

    private int revisionsFor(UUID employee) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM salary_revision WHERE employee_id = ?", Integer.class, employee);
    }

    @TestConfiguration
    static class ALogThatCannotWrite {

        @Bean
        @Primary
        SalaryRevisionLog aLogThatCannotWrite(JdbcTemplate jdbc) {
            return new SalaryRevisionLog() {

                @Override
                public void append(SalaryRevision revision) {
                    // Missing every NOT NULL column but two. The database refuses it, which is
                    // the point: a real failed INSERT after a real issued UPDATE.
                    jdbc.update(
                            "INSERT INTO salary_revision (id, employee_id) VALUES (?, ?)",
                            UUID.randomUUID(),
                            revision.employeeId().value());
                }
            };
        }
    }
}
