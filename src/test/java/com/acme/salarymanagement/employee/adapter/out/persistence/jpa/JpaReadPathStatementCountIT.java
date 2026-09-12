package com.acme.salarymanagement.employee.adapter.out.persistence.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

import com.acme.salarymanagement.employee.application.port.in.SalaryRevisionView;
import com.acme.salarymanagement.employee.domain.EmployeeId;
import com.acme.salarymanagement.support.CountingDataSource;
import com.acme.salarymanagement.support.PostgresIntegrationTest;

/**
 * The JPA read path, measured both ways.
 *
 * <p>This is the slice ADR-0014 scheduled: one mapped read, written so the obvious version is an
 * N+1, caught by the same statement-count harness the rest of the suite uses, and fixed with an
 * entity graph. The numbers asserted here are the numbers in
 * {@code docs/evidence/09-jpa-read-path.txt}.
 *
 * <p>Thirty revisions is roughly what a seeded employee carries, and it is where the difference
 * stops being academic: thirty-one statements to render one screen.
 */
@Transactional
@WithMockUser(roles = "HR_ANALYST")
class JpaReadPathStatementCountIT extends PostgresIntegrationTest {

    private static final int REVISIONS = 30;

    @Autowired
    private JpaSalaryRevisionReader reader;

    @Autowired
    private JdbcTemplate jdbc;

    private EmployeeId employee;

    @BeforeEach
    void anEmployeeWithAThirtyEntryHistory() {
        employee = new EmployeeId(UUID.randomUUID());
        jdbc.update(
                """
                INSERT INTO employee (id, employee_number, given_name, family_name, email, country_code,
                                      department, job_title, seniority_level, hire_date, status,
                                      salary_amount, salary_currency)
                VALUES (?, ?, 'Asha', 'Zzzjpa', ?, 'US', 'Zzzjpa', 'Zzzjpa', 'SENIOR',
                        DATE '2024-04-01', 'ACTIVE', 100000.00, 'USD')
                """,
                employee.value(),
                "JPA-" + employee.value(),
                employee.value() + "@acme.example");

        for (int i = 0; i < REVISIONS; i++) {
            // A different manager each time. With one manager the persistence context caches the
            // user after the first load and the N+1 collapses to two statements - see
            // a_repeated_author_hides_the_problem_behind_the_first_level_cache.
            UUID actor = newManager();
            jdbc.update(
                    """
                    INSERT INTO salary_revision (id, employee_id, previous_amount, new_amount, currency_code,
                                                 change_reason, changed_by, changed_at, note)
                    VALUES (?, ?, ?, ?, 'USD', 'MERIT', ?, ?, null)
                    """,
                    UUID.randomUUID(),
                    employee.value(),
                    new BigDecimal(100000 + i * 1000),
                    new BigDecimal(101000 + i * 1000),
                    actor,
                    java.sql.Timestamp.from(
                            Instant.parse("2026-01-01T00:00:00Z").plusSeconds(i * 3600L)));
        }
    }

    @Test
    void a_repeated_author_hides_the_problem_behind_the_first_level_cache() {
        EmployeeId sameManagerThroughout = anEmployeeWhosePayOnePersonAlwaysChanged();

        long statements = CountingDataSource.statementsIssuedBy(
                () -> reader.withoutTheEntityGraph(sameManagerThroughout, REVISIONS));

        // Two, not thirty-one: Hibernate loads that manager once and the persistence context
        // answers the other twenty-nine. This is why an N+1 can pass unnoticed in a test and
        // appear in production - the count scales with how many *distinct* users appear in the
        // log, not with how many rows it has.
        assertThat(statements).isEqualTo(2);
    }

    @Test
    void the_obvious_mapping_costs_one_statement_per_revision() {
        long statements =
                CountingDataSource.statementsIssuedBy(() -> reader.withoutTheEntityGraph(employee, REVISIONS));

        // One select for the revisions, then one more per row the moment the email is read. The
        // N+1 only appears because something renders who made the change: a lazy association
        // nobody touches looks free right up until a screen needs it.
        assertThat(statements).isEqualTo(REVISIONS + 1);
    }

    @Test
    void the_entity_graph_makes_it_one() {
        long statements = CountingDataSource.statementsIssuedBy(() -> reader.of(employee, REVISIONS));

        assertThat(statements).isEqualTo(1);
    }

    private EmployeeId anEmployeeWhosePayOnePersonAlwaysChanged() {
        UUID actor = newManager();
        EmployeeId theirs = new EmployeeId(UUID.randomUUID());
        jdbc.update(
                """
                INSERT INTO employee (id, employee_number, given_name, family_name, email, country_code,
                                      department, job_title, seniority_level, hire_date, status,
                                      salary_amount, salary_currency)
                VALUES (?, ?, 'Asha', 'Zzzjpa', ?, 'US', 'Zzzjpa', 'Zzzjpa', 'SENIOR',
                        DATE '2024-04-01', 'ACTIVE', 100000.00, 'USD')
                """,
                theirs.value(),
                "JPA1-" + theirs.value(),
                theirs.value() + "@acme.example");
        for (int i = 0; i < REVISIONS; i++) {
            insertRevision(theirs, actor, i);
        }
        return theirs;
    }

    private UUID newManager() {
        UUID actor = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO app_user (id, email, password_hash, role) VALUES (?, ?, 'not-a-real-hash', 'HR_MANAGER')",
                actor,
                actor + "@acme.example");
        return actor;
    }

    private void insertRevision(EmployeeId who, UUID actor, int index) {
        jdbc.update(
                """
                INSERT INTO salary_revision (id, employee_id, previous_amount, new_amount, currency_code,
                                             change_reason, changed_by, changed_at, note)
                VALUES (?, ?, ?, ?, 'USD', 'MERIT', ?, ?, null)
                """,
                UUID.randomUUID(),
                who.value(),
                new BigDecimal(100000 + index * 1000),
                new BigDecimal(101000 + index * 1000),
                actor,
                java.sql.Timestamp.from(Instant.parse("2026-01-01T00:00:00Z").plusSeconds(index * 3600L)));
    }

    @Test
    void both_readings_return_the_same_history() {
        var withGraph = reader.of(employee, REVISIONS);
        var without = reader.withoutTheEntityGraph(employee, REVISIONS);

        // The fix is a fetching strategy, not a different query. If the two disagreed, the faster
        // one would be faster at answering a different question.
        assertThat(withGraph).hasSize(REVISIONS).containsExactlyElementsOf(without);
        assertThat(withGraph).allSatisfy(revision -> assertThat(revision.changedByEmail())
                .as("the email is what makes the association load")
                .endsWith("@acme.example"));
    }

    @Test
    void the_log_comes_back_newest_first() {
        var history = reader.of(employee, REVISIONS);

        assertThat(history)
                .extracting(SalaryRevisionView::changedAt)
                .isSortedAccordingTo(java.util.Comparator.reverseOrder());
    }
}
