package com.acme.salarymanagement.employee.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
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
 * They are therefore swept afterwards - an unfiltered directory or dashboard query reads every
 * employee in the table, and a `Zzzconcurrent` department turning up in a filter dropdown is a
 * confusing way to learn that this class littered.
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
    void no_two_revisions_can_claim_the_same_starting_salary() throws Exception {
        CyclicBarrier bothReady = new CyclicBarrier(2);
        AtomicInteger accepted = new AtomicInteger();
        AtomicInteger refused = new AtomicInteger();

        // @WithMockUser puts the authentication on the test's own thread, and authorisation is at
        // the use-case layer - so each manager has to carry it onto their thread explicitly.
        var authenticated = org.springframework.security.core.context.SecurityContextHolder.getContext();

        java.util.function.Function<String, Callable<Void>> manager = target -> () -> {
            org.springframework.security.core.context.SecurityContextHolder.setContext(authenticated);
            bothReady.await(10, TimeUnit.SECONDS);
            try {
                changeSalary.change(new ChangeSalaryCommand(id, usd(target), ChangeReason.MERIT, actor, "raced"));
                accepted.incrementAndGet();
            } catch (ConcurrentSalaryChange refusedChange) {
                // Only this one is caught. A guard that failed some other way - a lock timeout, a
                // constraint - would surface as a 500 rather than a 409, and a broader catch would
                // count that as success.
                refused.incrementAndGet();
            }
            return null;
        };

        ExecutorService managers = Executors.newFixedThreadPool(2);
        try {
            // Two different figures on purpose. With the same figure, a schedule where one manager
            // finishes before the other starts reading makes the second a no-op that the aggregate
            // refuses for an unrelated reason - so the test would be asserting the scheduler.
            for (Future<Void> attempt :
                    managers.invokeAll(List.of(manager.apply("140000.00"), manager.apply("150000.00")))) {
                attempt.get(20, TimeUnit.SECONDS);
            }
        } finally {
            managers.shutdownNow();
        }

        // The invariant, true under either schedule and false the moment the guard goes: no two
        // revisions may claim to have started from the same salary. Interleaved, one manager is
        // refused and one revision exists. Serialised, both succeed and the second starts from
        // where the first finished - two revisions, two different starting figures. Unguarded, the
        // interleaved case writes two revisions both starting at 100,000, which is the history
        // that never happened.
        assertThat(distinctStartingSalaries()).isEqualTo(revisionCount());
        assertThat(accepted.get() + refused.get()).isEqualTo(2);
        assertThat(accepted.get()).isPositive();
        // Whatever the order, the salary on record is what the last accepted change wrote.
        assertThat(currentSalary()).isEqualByComparingTo(lastRevisionAmount());
    }

    private BigDecimal currentSalary() {
        return jdbc.queryForObject("SELECT salary_amount FROM employee WHERE id = ?", BigDecimal.class, id.value());
    }

    private long distinctStartingSalaries() {
        return jdbc.queryForObject(
                "SELECT count(DISTINCT previous_amount) FROM salary_revision WHERE employee_id = ?",
                Long.class,
                id.value());
    }

    private BigDecimal lastRevisionAmount() {
        return jdbc.queryForObject(
                "SELECT new_amount FROM salary_revision WHERE employee_id = ? ORDER BY changed_at DESC LIMIT 1",
                BigDecimal.class,
                id.value());
    }

    private long revisionCount() {
        return jdbc.queryForObject(
                "SELECT count(*) FROM salary_revision WHERE employee_id = ?", Long.class, id.value());
    }

    private static Money usd(String amount) {
        return Money.of(new BigDecimal(amount), USD);
    }

    /** Committed rows, swept: an unfiltered directory or dashboard query reads all of them. */
    @org.junit.jupiter.api.AfterEach
    void removeWhatThisTestCommitted() throws Exception {
        try (java.sql.Connection owner = com.acme.salarymanagement.support.DatabaseOwner.connect()) {
            try (var revisions = owner.prepareStatement("DELETE FROM salary_revision WHERE employee_id = ?")) {
                revisions.setObject(1, id.value());
                revisions.executeUpdate();
            }
            try (var employee = owner.prepareStatement("DELETE FROM employee WHERE id = ?")) {
                employee.setObject(1, id.value());
                employee.executeUpdate();
            }
            try (var user = owner.prepareStatement("DELETE FROM app_user WHERE id = ?")) {
                user.setObject(1, actor.value());
                user.executeUpdate();
            }
        }
    }
}
