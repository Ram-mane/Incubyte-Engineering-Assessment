package com.acme.salarymanagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.flyway.FlywayDataSource;
import org.springframework.jdbc.core.JdbcTemplate;

import com.acme.salarymanagement.support.PostgresIntegrationTest;
import com.zaxxer.hikari.HikariDataSource;

/**
 * The two pools are two identities, and this is the test that says so.
 *
 * <p>Production failed with {@code permission denied for table flyway_schema_history} while
 * Flyway read its own history. That error can only be raised at a connection running as
 * {@code salary_app}: the application role is granted nothing on Flyway's bookkeeping table, and
 * deliberately so (D100). So the question this pins down is not whether the grant is right - it
 * is which role each pool actually connects as.
 *
 * <p>A fresh database hides the failure. On a first boot Flyway <em>creates</em> the history
 * table, and as {@code salary_app} that fails differently, at the schema rather than the table.
 * "Permission denied for the table" is the symptom of a boot against a database that has already
 * been migrated - which every environment is, from its second deploy onwards, and which no
 * Testcontainers run ever is by default.
 */
class FlywayConnectsAsTheOwnerIT extends PostgresIntegrationTest {

    @Autowired
    @FlywayDataSource
    private DataSource flywayPool;

    @Autowired
    private DataSource applicationPool;

    @Autowired
    private JdbcTemplate applicationJdbc;

    @Autowired
    private Flyway flyway;

    @Test
    void flyway_actually_migrates_over_the_migration_pool() {
        // The previous assertions prove the migration pool is clean; this one proves Flyway uses
        // it. Without this, a resolution change that quietly handed Flyway the application pool
        // would leave every other test in this class passing.
        assertThat(flyway.getConfiguration().getDataSource())
                .as("@FlywayDataSource is a qualifier, and a qualifier only helps if it is honoured")
                .isSameAs(flywayPool);
    }

    @Test
    void the_migration_pool_connects_as_the_login_user_and_not_as_the_restricted_role() throws SQLException {
        assertThat(roleOf(flywayPool))
                .as("a migration that runs as salary_app cannot read its own history, "
                        + "cannot create a table, and fails the deploy")
                .isEqualTo(loginUser())
                .isNotEqualTo("salary_app");
    }

    @Test
    void the_application_pool_connects_as_the_restricted_role() throws SQLException {
        assertThat(roleOf(applicationPool))
                .as("the append-only grant binds the running system only if this is true")
                .isEqualTo("salary_app");
    }

    @Test
    void the_migration_pool_carries_no_session_state_of_its_own() {
        assertThat(((HikariDataSource) flywayPool).getConnectionInitSql())
                .as("SET ROLE on the migration pool is the production failure, exactly")
                .isNull();
    }

    @Test
    void the_migration_pool_can_read_the_history_that_the_application_pool_cannot() {
        JdbcTemplate migrations = new JdbcTemplate(flywayPool);

        assertThat(migrations.queryForObject("SELECT count(*) FROM flyway_schema_history", Integer.class))
                .as("this is the read that failed in production")
                .isPositive();

        assertThatThrownBy(() ->
                        applicationJdbc.queryForObject("SELECT count(*) FROM flyway_schema_history", Integer.class))
                .as("and this is why it mattered: the application role has no business there")
                .rootCause()
                .hasMessageContaining("permission denied for table flyway_schema_history");
    }

    private static String roleOf(DataSource pool) throws SQLException {
        try (var connection = pool.getConnection();
                var statement = connection.prepareStatement("SELECT current_user")) {
            try (var result = statement.executeQuery()) {
                result.next();
                return result.getString(1);
            }
        }
    }

    private String loginUser() throws SQLException {
        try (var connection = flywayPool.getConnection();
                var statement = connection.prepareStatement("SELECT session_user")) {
            try (var result = statement.executeQuery()) {
                result.next();
                return result.getString(1);
            }
        }
    }
}
