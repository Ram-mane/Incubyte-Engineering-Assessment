package com.acme.salarymanagement.employee.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.acme.salarymanagement.employee.application.port.in.ChangeSalary;
import com.acme.salarymanagement.employee.application.port.in.ChangeSalaryCommand;
import com.acme.salarymanagement.employee.application.port.in.GetSalaryRevisions;
import com.acme.salarymanagement.employee.application.service.EmployeeNotFound;
import com.acme.salarymanagement.employee.domain.ChangeReason;
import com.acme.salarymanagement.employee.domain.EmployeeId;
import com.acme.salarymanagement.employee.domain.UserId;
import com.acme.salarymanagement.shared.CurrencyCode;
import com.acme.salarymanagement.shared.Money;
import com.acme.salarymanagement.support.CountingDataSource;
import com.acme.salarymanagement.support.PostgresIntegrationTest;

/** A pay change, end to end: what moves, what is written down, and what is refused. */
@Transactional
class ChangeSalaryIT extends PostgresIntegrationTest {

    private static final CurrencyCode INR = new CurrencyCode("INR");
    private static final CurrencyCode EUR = new CurrencyCode("EUR");

    @Autowired
    private ChangeSalary changeSalary;

    @Autowired
    private GetSalaryRevisions revisions;

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
                        DATE '2024-04-01', 'ACTIVE', 1200000.0000, 'INR')
                """,
                employeeId,
                "CS-" + employeeId,
                employeeId + "@acme.example");
    }

    @Test
    void the_employee_is_paid_the_new_amount() {
        var updated = changeSalary.change(aRaiseTo("1380000.00"));

        assertThat(updated.salary()).isEqualTo(Money.of("1380000.00", INR));
        assertThat(storedSalary()).isEqualByComparingTo("1380000.0000");
    }

    @Test
    void the_change_is_written_down_with_what_it_was_before() {
        changeSalary.change(aRaiseTo("1380000.00"));

        var row = latestRevision();
        assertThat((BigDecimal) row.get("previous_amount")).isEqualByComparingTo("1200000.0000");
        assertThat((BigDecimal) row.get("new_amount")).isEqualByComparingTo("1380000.0000");
        assertThat(row.get("change_reason")).isEqualTo("MERIT");
        assertThat(row.get("changed_by")).isEqualTo(actorId);
        assertThat(row.get("note")).isEqualTo("Annual merit review");
    }

    @Test
    void the_recorded_instant_comes_from_the_boundary_rather_than_the_adapter() {
        Instant before = Instant.now();

        changeSalary.change(aRaiseTo("1380000.00"));

        Instant recorded = ((java.sql.Timestamp) latestRevision().get("changed_at")).toInstant();
        // Nothing between the use case and the row reads a clock: the instant the application
        // resolved is the instant stored, so it cannot drift from what the aggregate was told.
        assertThat(recorded).isBetween(before.minusSeconds(5), Instant.now().plusSeconds(5));
    }

    @Test
    void a_change_credited_to_a_user_who_does_not_exist_is_refused() {
        var command = new ChangeSalaryCommand(
                new EmployeeId(employeeId),
                Money.of("1380000.00", INR),
                ChangeReason.MERIT,
                new UserId(UUID.randomUUID()),
                null);

        assertThatThrownBy(() -> changeSalary.change(command))
                .as("an audit record whose author is a guess is not an audit record")
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(storedSalary()).isEqualByComparingTo("1200000.0000");
    }

    @Test
    void changing_the_pay_of_somebody_who_is_not_here_is_refused() {
        var command = new ChangeSalaryCommand(
                new EmployeeId(UUID.randomUUID()),
                Money.of("1380000.00", INR),
                ChangeReason.MERIT,
                new UserId(actorId),
                null);

        assertThatThrownBy(() -> changeSalary.change(command)).isInstanceOf(EmployeeNotFound.class);
    }

    @Test
    void a_salary_in_the_wrong_currency_never_reaches_the_database() {
        var command = new ChangeSalaryCommand(
                new EmployeeId(employeeId),
                Money.of("85000.00", EUR),
                ChangeReason.MARKET_ADJUSTMENT,
                new UserId(actorId),
                null);

        assertThatThrownBy(() -> changeSalary.change(command)).isInstanceOf(IllegalArgumentException.class);
        assertThat(storedSalary()).isEqualByComparingTo("1200000.0000");
        assertThat(revisions.of(new EmployeeId(employeeId), 50)).isEmpty();
    }

    @Test
    void the_log_reads_newest_first() {
        changeSalary.change(aRaiseTo("1300000.00"));
        changeSalary.change(aRaiseTo("1380000.00"));
        changeSalary.change(aRaiseTo("1450000.00"));

        assertThat(revisions.of(new EmployeeId(employeeId), 50))
                .extracting(revision -> revision.newAmount().amount().toPlainString())
                .containsExactly("1450000.00", "1380000.00", "1300000.00");
    }

    @Test
    void reading_the_log_costs_one_statement_however_long_it_is() {
        changeSalary.change(aRaiseTo("1300000.00"));
        changeSalary.change(aRaiseTo("1380000.00"));

        long statements = CountingDataSource.statementsIssuedBy(() -> revisions.of(new EmployeeId(employeeId), 50));

        // The obvious N+1 here is fetching the employee, or the actor, once per revision.
        assertThat(statements).isEqualTo(1);
    }

    private ChangeSalaryCommand aRaiseTo(String amount) {
        return new ChangeSalaryCommand(
                new EmployeeId(employeeId),
                Money.of(amount, INR),
                ChangeReason.MERIT,
                new UserId(actorId),
                "Annual merit review");
    }

    private BigDecimal storedSalary() {
        return jdbc.queryForObject("SELECT salary_amount FROM employee WHERE id = ?", BigDecimal.class, employeeId);
    }

    private Map<String, Object> latestRevision() {
        return jdbc.queryForMap(
                "SELECT * FROM salary_revision WHERE employee_id = ? ORDER BY changed_at DESC, id DESC LIMIT 1",
                employeeId);
    }
}
