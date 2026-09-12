package com.acme.salarymanagement.employee.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;

import com.acme.salarymanagement.employee.application.port.in.ChangeSalary;
import com.acme.salarymanagement.employee.application.port.in.ChangeSalaryCommand;
import com.acme.salarymanagement.employee.application.port.out.EmployeeRepository;
import com.acme.salarymanagement.employee.domain.ChangeReason;
import com.acme.salarymanagement.employee.domain.ConcurrentSalaryChange;
import com.acme.salarymanagement.employee.domain.Employee;
import com.acme.salarymanagement.employee.domain.EmployeeId;
import com.acme.salarymanagement.employee.domain.UserId;
import com.acme.salarymanagement.shared.CurrencyCode;
import com.acme.salarymanagement.shared.Money;
import com.acme.salarymanagement.support.PostgresIntegrationTest;

/**
 * Two managers changing the same salary at once.
 *
 * <p>Without a guard both writes succeed: each reads 100,000, each writes its own figure, each
 * appends a revision saying it moved <em>from</em> 100,000, and the employee ends on whichever
 * committed last. The salary is then a number one of the two changes never produced, and the audit
 * log describes a history that never happened - which is worse than losing the update, because the
 * log is the thing this system exists to be trusted about.
 *
 * <p>Not {@code @Transactional}: these writes have to commit for a second connection to see them.
 * The employee is this test's own and nothing else reads it.
 */
@WithMockUser(roles = "HR_MANAGER")
class ConcurrentSalaryChangeIT extends PostgresIntegrationTest {

    private static final CurrencyCode USD = new CurrencyCode("USD");

    @Autowired
    private ChangeSalary changeSalary;

    @Autowired
    private EmployeeRepository employees;

    @Autowired
    private JdbcTemplate jdbc;

    private EmployeeId id;
    private UserId actor;

    @BeforeEach
    void oneEmployeeOnOneHundredThousand() {
        // Its own user: the demo accounts are seeded only under the seed profile, and a revision
        // has a foreign key to a real one.
        UUID actorId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO app_user (id, email, password_hash, role) VALUES (?, ?, 'not-a-real-hash', 'HR_MANAGER')",
                actorId,
                actorId + "@acme.example");
        actor = new UserId(actorId);
        id = new EmployeeId(UUID.randomUUID());
        jdbc.update(
                """
                INSERT INTO employee (id, employee_number, given_name, family_name, email, country_code,
                                      department, job_title, seniority_level, hire_date, status,
                                      salary_amount, salary_currency)
                VALUES (?, ?, 'Asha', 'Zzzconcurrent', ?, 'US', 'Zzzconcurrent', 'Zzzconcurrent',
                        'SENIOR', DATE '2024-04-01', 'ACTIVE', 100000.00, 'USD')
                """,
                id.value(),
                "CON-" + id.value(),
                id.value() + "@acme.example");
    }

    @Test
    void the_second_of_two_changes_from_the_same_starting_salary_is_refused() {
        Employee asFirstManagerSeesIt = employees.load(id).orElseThrow();
        Employee asSecondManagerSeesIt = employees.load(id).orElseThrow();
        var atOneTwenty = asFirstManagerSeesIt.changeSalaryTo(
                usd("120000.00"), ChangeReason.MERIT, actor, null, java.time.Instant.parse("2026-09-12T10:00:00Z"));
        var atOneThirty = asSecondManagerSeesIt.changeSalaryTo(
                usd("130000.00"), ChangeReason.MERIT, actor, null, java.time.Instant.parse("2026-09-12T10:00:01Z"));

        employees.saveCurrentSalaryOf(asFirstManagerSeesIt, atOneTwenty.previousAmount());

        // The second manager's revision says "from 100,000", and the employee is no longer on
        // 100,000. Writing it would record a step that never happened.
        assertThatThrownBy(() -> employees.saveCurrentSalaryOf(asSecondManagerSeesIt, atOneThirty.previousAmount()))
                .isInstanceOf(ConcurrentSalaryChange.class);
    }

    @Test
    void a_change_from_the_salary_actually_on_record_still_goes_through() {
        Employee employee = employees.load(id).orElseThrow();
        var revision = employee.changeSalaryTo(
                usd("110000.00"), ChangeReason.MERIT, actor, null, java.time.Instant.parse("2026-09-12T10:00:00Z"));

        employees.saveCurrentSalaryOf(employee, revision.previousAmount());

        assertThat(currentSalary()).isEqualByComparingTo("110000.00");
    }

    @Test
    void two_managers_changing_the_same_salary_at_once_leave_exactly_one_change_behind() throws Exception {
        CyclicBarrier bothReady = new CyclicBarrier(2);
        AtomicInteger accepted = new AtomicInteger();
        AtomicInteger refused = new AtomicInteger();

        // @WithMockUser puts the authentication on the test's own thread, and authorisation is at
        // the use-case layer - so each manager has to carry it onto their thread explicitly.
        var authenticated = org.springframework.security.core.context.SecurityContextHolder.getContext();

        Callable<Void> raise = () -> {
            org.springframework.security.core.context.SecurityContextHolder.setContext(authenticated);
            bothReady.await(10, TimeUnit.SECONDS);
            try {
                changeSalary.change(new ChangeSalaryCommand(id, usd("140000.00"), ChangeReason.MERIT, actor, "raced"));
                accepted.incrementAndGet();
            } catch (ConcurrentSalaryChange | org.springframework.dao.DataAccessException refusedChange) {
                refused.incrementAndGet();
            }
            return null;
        };

        ExecutorService managers = Executors.newFixedThreadPool(2);
        try {
            for (Future<Void> attempt : managers.invokeAll(List.of(raise, raise))) {
                attempt.get(20, TimeUnit.SECONDS);
            }
        } finally {
            managers.shutdownNow();
        }

        // One of them has to lose. What matters is that the loser is told, rather than both
        // succeeding and the audit log carrying two revisions from the same starting salary -
        // which is exactly what happens with the guard removed: accepted 2, revisions 2.
        assertThat(accepted.get()).isEqualTo(1);
        assertThat(refused.get()).isEqualTo(1);
        assertThat(revisionCount()).isEqualTo(1);
        assertThat(currentSalary()).isEqualByComparingTo("140000.00");
    }

    private BigDecimal currentSalary() {
        return jdbc.queryForObject("SELECT salary_amount FROM employee WHERE id = ?", BigDecimal.class, id.value());
    }

    private long revisionCount() {
        return jdbc.queryForObject(
                "SELECT count(*) FROM salary_revision WHERE employee_id = ?", Long.class, id.value());
    }

    private static Money usd(String amount) {
        return Money.of(new BigDecimal(amount), USD);
    }

    static {
        Locale.setDefault(Locale.ROOT);
    }
}
