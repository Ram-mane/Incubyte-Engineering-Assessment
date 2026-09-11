package com.acme.salarymanagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.acme.salarymanagement.support.PostgresIntegrationTest;

/**
 * The append-only guarantee, proved against the connection the application actually uses.
 *
 * <p>Every assertion here goes through the autowired {@link JdbcTemplate}, which is built on the
 * application's own {@code DataSource}, configured exactly as a deployed instance configures it -
 * including the {@code SET ROLE salary_app} that Hikari runs on every connection. This test issues
 * no {@code SET ROLE} of its own: a test that granted itself the privileges it is testing would
 * prove only that PostgreSQL has a permission system.
 *
 * <p>The two success cases are load-bearing. Without them the refusals would also pass on a
 * connection that was simply broken, or a role with no privileges at all.
 */
@Transactional
class SalaryRevisionIsAppendOnlyIT extends PostgresIntegrationTest {

    @Test
    void the_application_runs_as_the_restricted_role(@Autowired JdbcTemplate jdbc) {
        assertThat(jdbc.queryForObject("SELECT current_user", String.class))
                .as("if the application connects as the table's owner, every grant below is decorative")
                .isEqualTo("salary_app");
    }

    @Test
    void the_application_can_write_a_revision(@Autowired JdbcTemplate jdbc) {
        var employee = anEmployee(jdbc);

        assertThatCode(() -> aRevisionFor(jdbc, employee)).doesNotThrowAnyException();
    }

    @Test
    void the_application_can_read_the_log_it_wrote(@Autowired JdbcTemplate jdbc) {
        var employee = anEmployee(jdbc);
        aRevisionFor(jdbc, employee);

        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM salary_revision WHERE employee_id = ?", Integer.class, employee))
                .isEqualTo(1);
    }

    @Test
    void the_application_cannot_rewrite_what_someone_was_paid(@Autowired JdbcTemplate jdbc) {
        var employee = anEmployee(jdbc);
        aRevisionFor(jdbc, employee);

        assertThatThrownBy(() -> jdbc.update(
                        "UPDATE salary_revision SET new_amount = ? WHERE employee_id = ?",
                        new BigDecimal("9900000.0000"),
                        employee))
                .as("a salary change that can be edited afterwards is not an audit record")
                .isInstanceOf(DataAccessException.class)
                // Asserted on the message, not the type: PostgreSQL reports insufficient privilege
                // as SQLSTATE 42501, which Spring's translator lands in the 42xx syntax family.
                .rootCause()
                .hasMessageContaining("permission denied for table salary_revision");
    }

    @Test
    void the_application_cannot_erase_a_change_from_the_log(@Autowired JdbcTemplate jdbc) {
        var employee = anEmployee(jdbc);
        aRevisionFor(jdbc, employee);

        assertThatThrownBy(() -> jdbc.update("DELETE FROM salary_revision WHERE employee_id = ?", employee))
                .as("the gap left by a deleted revision is exactly what an audit log exists to prevent")
                .isInstanceOf(DataAccessException.class)
                .rootCause()
                .hasMessageContaining("permission denied for table salary_revision");
    }

    @Test
    void the_application_can_still_change_what_an_employee_is_paid(@Autowired JdbcTemplate jdbc) {
        var employee = anEmployee(jdbc);

        assertThatCode(() -> jdbc.update(
                        "UPDATE employee SET salary_amount = ? WHERE id = ?", new BigDecimal("1380000.0000"), employee))
                .as("only the log is frozen; pay itself still changes in place")
                .doesNotThrowAnyException();
    }

    private static UUID anEmployee(JdbcTemplate jdbc) {
        var id = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO employee (id, employee_number, given_name, family_name, email, country_code,
                                      department, job_title, seniority_level, hire_date, status,
                                      salary_amount, salary_currency)
                VALUES (?, ?, 'Asha', 'Rao', ?, 'IN', 'Engineering', 'Engineer', 'SENIOR',
                        DATE '2024-04-01', 'ACTIVE', 1200000.0000, 'INR')
                """,
                id,
                id.toString(),
                id + "@acme.example");
        return id;
    }

    private static void aRevisionFor(JdbcTemplate jdbc, UUID employee) {
        var actor = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO app_user (id, email, password_hash, role) VALUES (?, ?, 'not-a-real-hash', 'HR_MANAGER')",
                actor,
                actor + "@acme.example");
        jdbc.update(
                """
                INSERT INTO salary_revision (id, employee_id, previous_amount, new_amount, currency_code,
                                             change_reason, changed_by, changed_at, note)
                VALUES (?, ?, 1200000.0000, 1380000.0000, 'INR', 'MERIT', ?,
                        TIMESTAMPTZ '2026-09-11T09:15:30Z', 'Annual merit review')
                """,
                UUID.randomUUID(),
                employee,
                actor);
    }
}
