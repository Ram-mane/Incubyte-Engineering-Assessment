package com.acme.salarymanagement;

import static com.acme.salarymanagement.support.SchemaCatalogue.columnsOf;
import static com.acme.salarymanagement.support.SchemaCatalogue.generatedValuesIn;
import static com.acme.salarymanagement.support.SchemaCatalogue.indexesOn;
import static com.acme.salarymanagement.support.SchemaCatalogue.primaryKeyOf;
import static com.acme.salarymanagement.support.SchemaCatalogue.tablesInTheSchema;
import static com.acme.salarymanagement.support.SchemaCatalogue.triggersOn;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.acme.salarymanagement.support.PostgresIntegrationTest;

/**
 * What the schema actually is, asserted column by column against the catalogue.
 *
 * <p>A migration test that only asserts Flyway ran without throwing reports success while checking
 * nothing: every column could be the wrong type, nullable where the aggregate requires a value, or
 * missing outright, and it would still pass. So the expectations here are exact - the full column
 * set, each column's type and nullability, and the constraints proved by writing rows the database
 * has to refuse.
 *
 * <p>Transactional, so the rows written to prove a constraint roll back; the container is shared
 * across the suite and a test that leaves data behind is a test that breaks the next one.
 */
@Transactional
class EmployeeSchemaIT extends PostgresIntegrationTest {

    private static final String INSERT_EMPLOYEE =
            """
            INSERT INTO employee (id, employee_number, given_name, family_name, email, country_code,
                                  department, job_title, seniority_level, hire_date, status,
                                  salary_amount, salary_currency)
            VALUES (?, ?, 'Asha', 'Rao', ?, 'IN', 'Engineering', 'Engineer', ?, DATE '2024-04-01', ?, ?, 'INR')
            """;

    @Test
    void the_employee_table_holds_exactly_what_the_aggregate_holds(@Autowired JdbcTemplate jdbc) {
        assertThat(columnsOf(jdbc, "employee"))
                .containsExactlyInAnyOrderEntriesOf(Map.ofEntries(
                        Map.entry("id", "uuid NOT NULL"),
                        Map.entry("employee_number", "character varying NOT NULL"),
                        Map.entry("given_name", "character varying NOT NULL"),
                        Map.entry("family_name", "character varying NOT NULL"),
                        Map.entry("email", "character varying NOT NULL"),
                        Map.entry("country_code", "character(2) NOT NULL"),
                        Map.entry("department", "character varying NOT NULL"),
                        Map.entry("job_title", "character varying NOT NULL"),
                        Map.entry("seniority_level", "character varying NOT NULL"),
                        Map.entry("hire_date", "date NOT NULL"),
                        Map.entry("status", "character varying NOT NULL"),
                        Map.entry("salary_amount", "numeric(19,4) NOT NULL"),
                        Map.entry("salary_currency", "character(3) NOT NULL")));
    }

    @Test
    void the_app_user_table_holds_what_authentication_will_need(@Autowired JdbcTemplate jdbc) {
        assertThat(columnsOf(jdbc, "app_user"))
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        "id", "uuid NOT NULL",
                        "email", "character varying NOT NULL",
                        "password_hash", "character varying NOT NULL",
                        "role", "character varying NOT NULL"));
    }

    @Test
    void department_is_a_column_on_employee_rather_than_a_table_to_join(@Autowired JdbcTemplate jdbc) {
        assertThat(tablesInTheSchema(jdbc))
                .as("a lookup table would put a join in front of every query that groups by it: D092")
                .doesNotContain("department");
        assertThat(columnsOf(jdbc, "employee")).containsKey("department");
    }

    @Test
    void every_table_is_keyed_by_the_identity_the_application_assigned(@Autowired JdbcTemplate jdbc) {
        assertThat(primaryKeyOf(jdbc, "app_user")).containsExactly("id");
        assertThat(primaryKeyOf(jdbc, "employee")).containsExactly("id");
    }

    @Test
    void no_table_mints_an_identity_or_a_timestamp_of_its_own(@Autowired JdbcTemplate jdbc) {
        for (String table : List.of("app_user", "employee")) {
            assertThat(generatedValuesIn(jdbc, table))
                    .as(
                            "%s must have no column default: a gen_random_uuid() or now() default is a"
                                    + " second source of identity and a clock the domain cannot see",
                            table)
                    .isEmpty();
            assertThat(triggersOn(jdbc, table))
                    .as("%s must have no trigger: a trigger that stamps a row is ambient time one layer down", table)
                    .isEmpty();
        }
    }

    @Test
    void a_salary_keeps_every_digit_it_was_given(@Autowired JdbcTemplate jdbc) {
        insertEmployee(jdbc, "E-1001", "SENIOR", "ACTIVE", "1234567.8900");

        var stored = jdbc.queryForObject(
                "SELECT salary_amount FROM employee WHERE employee_number = ?", BigDecimal.class, "E-1001");

        assertThat(stored)
                .as("numeric holds pay exactly; a float column would round it and nobody would see where")
                .isEqualByComparingTo("1234567.8900");
    }

    @Test
    void a_salary_of_zero_is_rejected_by_the_database(@Autowired JdbcTemplate jdbc) {
        assertThatThrownBy(() -> insertEmployee(jdbc, "E-2001", "SENIOR", "ACTIVE", "0.0000"))
                .as("I1 is enforced in Employee and again here, because bulk import writes rows too")
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("employee_salary_is_positive");
    }

    @Test
    void an_employment_status_the_domain_does_not_have_is_rejected(@Autowired JdbcTemplate jdbc) {
        assertThatThrownBy(() -> insertEmployee(jdbc, "E-3001", "SENIOR", "ON_LEAVE", "1200000.0000"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("employee_status_is_known");
    }

    @Test
    void a_seniority_level_the_domain_does_not_have_is_rejected(@Autowired JdbcTemplate jdbc) {
        assertThatThrownBy(() -> insertEmployee(jdbc, "E-4001", "ARCHITECT", "ACTIVE", "1200000.0000"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("employee_seniority_level_is_known");
    }

    @Test
    void the_same_employee_number_cannot_be_used_twice(@Autowired JdbcTemplate jdbc) {
        insertEmployee(jdbc, "E-6001", "SENIOR", "ACTIVE", "1200000.0000");

        assertThatThrownBy(() -> insertEmployee(jdbc, "E-6001", "MID", "ACTIVE", "900000.0000"))
                .as("the company's own identifier for a person identifies one person")
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void the_directory_filter_columns_are_indexed(@Autowired JdbcTemplate jdbc) {
        var definitions = indexesOn(jdbc, "employee");

        for (String column : List.of("country_code", "department", "job_title", "seniority_level")) {
            assertThat(definitions)
                    .as("the directory filters on %s, and 2.6 queries it against ten thousand rows", column)
                    .anyMatch(definition -> definition.contains("(" + column + ")"));
        }
    }

    private static void insertEmployee(JdbcTemplate jdbc, String number, String level, String status, String salary) {
        jdbc.update(
                INSERT_EMPLOYEE,
                UUID.randomUUID(),
                number,
                number.toLowerCase(Locale.ROOT) + "@acme.example",
                level,
                status,
                new BigDecimal(salary));
    }

    private static List<String> tablesInTheSchema(JdbcTemplate jdbc) {
        return jdbc.queryForList("SELECT tablename FROM pg_tables WHERE schemaname = ?", String.class, "public");
    }
}
