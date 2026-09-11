package com.acme.salarymanagement;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.acme.salarymanagement.support.PostgresIntegrationTest;

/**
 * The application runs as a role with no privileges it was not given, so a table created without
 * a grant is a table the application cannot read. That failure has no good moment to be found:
 * the migration succeeds, the context starts, and the first query in the first screen that needs
 * it returns "permission denied".
 *
 * <p>So the convention - every migration that creates a table grants it in the same file (D100) -
 * is checked rather than remembered. Enumeration comes from {@code pg_tables} and the check from
 * {@code has_table_privilege}, both of which see the whole schema: {@code information_schema}
 * would have hidden exactly the tables this is looking for, because it lists only what the
 * current user already has some privilege on.
 */
class SchemaGrantsIT extends PostgresIntegrationTest {

    private static final String APPLICATION_ROLE = "salary_app";

    /** Flyway's own bookkeeping, which belongs to the migration user and not to the application. */
    private static final String NOT_THE_APPLICATIONS = "flyway_schema_history";

    @Test
    void every_table_grants_the_application_role_at_least_select(@Autowired JdbcTemplate jdbc) {
        List<String> ungranted = jdbc.queryForList(
                """
                SELECT tablename
                FROM   pg_tables
                WHERE  schemaname = ?
                  AND  tablename <> ?
                  AND  NOT has_table_privilege(?, quote_ident(schemaname) || '.' || quote_ident(tablename), 'SELECT')
                ORDER BY tablename
                """,
                String.class,
                "public",
                NOT_THE_APPLICATIONS,
                APPLICATION_ROLE);

        assertThat(ungranted)
                .as("a table created without a GRANT block fails here, not at its first query")
                .isEmpty();
    }

    @Test
    void the_migration_history_is_not_the_applications_to_read(@Autowired JdbcTemplate jdbc) {
        assertThat(jdbc.queryForObject(
                        "SELECT has_table_privilege(?, ?, 'SELECT')",
                        Boolean.class,
                        APPLICATION_ROLE,
                        NOT_THE_APPLICATIONS))
                .as("the one table excluded above is excluded because it really is ungranted")
                .isFalse();
    }
}
