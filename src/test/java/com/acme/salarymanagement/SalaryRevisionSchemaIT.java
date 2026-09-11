package com.acme.salarymanagement;

import static com.acme.salarymanagement.support.SchemaCatalogue.columnsOf;
import static com.acme.salarymanagement.support.SchemaCatalogue.generatedValuesIn;
import static com.acme.salarymanagement.support.SchemaCatalogue.indexesOn;
import static com.acme.salarymanagement.support.SchemaCatalogue.primaryKeyOf;
import static com.acme.salarymanagement.support.SchemaCatalogue.privilegesOn;
import static com.acme.salarymanagement.support.SchemaCatalogue.triggersOn;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.acme.salarymanagement.employee.domain.ChangeReason;
import com.acme.salarymanagement.support.PostgresIntegrationTest;

/**
 * The shape of the audit log, and the privileges that make it append-only.
 *
 * <p>The domain cannot change pay without producing a {@code SalaryRevision}, but that guarantee
 * only reaches as far as the code: a stray {@code UPDATE} would rewrite history and the aggregate
 * would never know. So the table carries the same invariants the domain does, and the application
 * role is granted no way to alter a row once written. This asserts the privileges the migration
 * hands out; 2.3 proves the database actually refuses the statements.
 */
@Transactional
class SalaryRevisionSchemaIT extends PostgresIntegrationTest {

    private static final String APPLICATION_ROLE = "salary_app";

    private static final String INSERT_REVISION =
            """
            INSERT INTO salary_revision (id, employee_id, previous_amount, new_amount, currency_code,
                                         change_reason, changed_by, changed_at, note)
            VALUES (?, ?, ?, ?, 'INR', ?, ?, TIMESTAMPTZ '2026-09-11T09:15:30Z', 'Annual merit review')
            """;

    @Test
    void the_revision_table_holds_exactly_what_the_domain_record_holds(@Autowired JdbcTemplate jdbc) {
        assertThat(columnsOf(jdbc, "salary_revision"))
                .containsExactlyInAnyOrderEntriesOf(Map.ofEntries(
                        Map.entry("id", "uuid NOT NULL"),
                        Map.entry("employee_id", "uuid NOT NULL"),
                        Map.entry("previous_amount", "numeric(19,4) NOT NULL"),
                        Map.entry("new_amount", "numeric(19,4) NOT NULL"),
                        Map.entry("currency_code", "character(3) NOT NULL"),
                        Map.entry("change_reason", "character varying NOT NULL"),
                        Map.entry("changed_by", "uuid NOT NULL"),
                        Map.entry("changed_at", "timestamp with time zone NOT NULL"),
                        Map.entry("note", "character varying NULL")));
    }

    @Test
    void a_revision_mints_neither_its_own_key_nor_its_own_timestamp(@Autowired JdbcTemplate jdbc) {
        assertThat(primaryKeyOf(jdbc, "salary_revision")).containsExactly("id");
        assertThat(generatedValuesIn(jdbc, "salary_revision"))
                .as("the adapter assigns the id and writes the Instant it was passed: D091, D094")
                .isEmpty();
        assertThat(triggersOn(jdbc, "salary_revision")).isEmpty();
    }

    @Test
    void a_revision_records_a_change_that_actually_happened(@Autowired JdbcTemplate jdbc) {
        var employee = anEmployee(jdbc);
        var actor = anHrManager(jdbc);

        insertRevision(jdbc, employee, actor, "1200000.0000", "1380000.0000", "MERIT");

        assertThat(jdbc.queryForObject(
                        "SELECT new_amount FROM salary_revision WHERE employee_id = ?", BigDecimal.class, employee))
                .isEqualByComparingTo("1380000.0000");
    }

    @Test
    void a_revision_of_nobody_is_rejected(@Autowired JdbcTemplate jdbc) {
        var actor = anHrManager(jdbc);

        assertThatThrownBy(
                        () -> insertRevision(jdbc, UUID.randomUUID(), actor, "1200000.0000", "1380000.0000", "MERIT"))
                .as("an audit record of a change to an employee who does not exist accounts for nothing")
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void a_revision_by_nobody_is_rejected(@Autowired JdbcTemplate jdbc) {
        var employee = anEmployee(jdbc);

        assertThatThrownBy(() ->
                        insertRevision(jdbc, employee, UUID.randomUUID(), "1200000.0000", "1380000.0000", "MERIT"))
                .as("who changed it is the point of the record")
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void a_reason_the_domain_does_not_have_is_rejected(@Autowired JdbcTemplate jdbc) {
        var employee = anEmployee(jdbc);
        var actor = anHrManager(jdbc);

        assertThatThrownBy(() -> insertRevision(jdbc, employee, actor, "1200000.0000", "1380000.0000", "HIRE"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("salary_revision_reason_is_known");
    }

    @Test
    void a_revision_that_changes_nothing_is_rejected(@Autowired JdbcTemplate jdbc) {
        var employee = anEmployee(jdbc);
        var actor = anHrManager(jdbc);

        assertThatThrownBy(() -> insertRevision(jdbc, employee, actor, "1200000.0000", "1200000.0000", "MERIT"))
                .as("I6: a no-op is not a change, and an audit log of non-changes is noise")
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("salary_revision_is_a_change");
    }

    @Test
    void a_revision_to_an_amount_nobody_could_be_paid_is_rejected(@Autowired JdbcTemplate jdbc) {
        var employee = anEmployee(jdbc);
        var actor = anHrManager(jdbc);

        assertThatThrownBy(() -> insertRevision(jdbc, employee, actor, "1200000.0000", "0.0000", "CORRECTION"))
                .as("I1 holds on the log as well as on the employee: changeSalaryTo refuses it too")
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("salary_revision_new_amount_is_positive");
    }

    @Test
    void a_revision_from_an_amount_nobody_could_be_paid_is_allowed(@Autowired JdbcTemplate jdbc) {
        var employee = anEmployee(jdbc);
        var actor = anHrManager(jdbc);

        // Deliberately no CHECK on previous_amount. Money holds zero and negative amounts by
        // design and Employee's constructor does not require a positive salary, so the domain can
        // legitimately produce this row - and a database-only rule would reject it at 2.10 (D096).
        assertThatCode(() -> insertRevision(jdbc, employee, actor, "0.0000", "1200000.0000", "CORRECTION"))
                .doesNotThrowAnyException();
    }

    @Test
    void the_reason_check_allows_exactly_the_reasons_the_domain_has(@Autowired JdbcTemplate jdbc) {
        assertThat(allowedReasonsIn(jdbc))
                .as("the enum and the CHECK are the same set written twice; drift fails here rather"
                        + " than at insert time, for a reason the domain considers perfectly valid")
                .containsExactlyInAnyOrderElementsOf(
                        Stream.of(ChangeReason.values()).map(Enum::name).toList());
    }

    @Test
    void an_employees_own_log_is_indexed_newest_first(@Autowired JdbcTemplate jdbc) {
        assertThat(indexesOn(jdbc, "salary_revision"))
                .as("GET /employees/{id}/salary-revisions pages by (changed_at DESC, id DESC) at 2.7")
                .anyMatch(definition -> definition.contains("(employee_id, changed_at DESC, id DESC)"));
    }

    @Test
    void the_application_role_can_write_a_revision_but_never_rewrite_one(@Autowired JdbcTemplate jdbc) {
        assertThat(privilegesOn(jdbc, APPLICATION_ROLE, "salary_revision"))
                .as("D020: the append-only guarantee is the database's, not the repository's")
                .containsExactlyInAnyOrder("SELECT", "INSERT");
    }

    @Test
    void the_application_role_can_still_change_what_an_employee_is_paid(@Autowired JdbcTemplate jdbc) {
        assertThat(privilegesOn(jdbc, APPLICATION_ROLE, "employee"))
                .as("pay changes in place; only the log is frozen")
                .containsExactlyInAnyOrder("SELECT", "INSERT", "UPDATE");
    }

    /** The values the CHECK actually permits, read out of the constraint rather than restated. */
    private static Set<String> allowedReasonsIn(JdbcTemplate jdbc) {
        String definition = jdbc.queryForObject(
                """
                SELECT pg_get_constraintdef(oid)
                FROM   pg_constraint
                WHERE  conrelid = to_regclass(?) AND conname = ?
                """,
                String.class,
                "salary_revision",
                "salary_revision_reason_is_known");

        var reasons = new LinkedHashSet<String>();
        var literals = Pattern.compile("'([A-Z_]+)'").matcher(definition);
        while (literals.find()) {
            reasons.add(literals.group(1));
        }
        return reasons;
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

    private static UUID anHrManager(JdbcTemplate jdbc) {
        var id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO app_user (id, email, password_hash, role) VALUES (?, ?, 'not-a-real-hash', 'HR_MANAGER')",
                id,
                id + "@acme.example");
        return id;
    }

    private static void insertRevision(
            JdbcTemplate jdbc, UUID employee, UUID actor, String previous, String current, String reason) {
        jdbc.update(
                INSERT_REVISION,
                UUID.randomUUID(),
                employee,
                new BigDecimal(previous),
                new BigDecimal(current),
                reason,
                actor);
    }
}
