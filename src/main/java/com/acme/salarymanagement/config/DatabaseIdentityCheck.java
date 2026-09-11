package com.acme.salarymanagement.config;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.flyway.FlywayDataSource;
import org.springframework.stereotype.Component;

/**
 * Proves, at startup, that each pool is the identity it is supposed to be.
 *
 * <p>The append-only guarantee is a grant, and a grant only binds the role that actually connects
 * (D097). That role is established by {@code SET ROLE salary_app} in the application pool's
 * connection-init SQL - which is <em>session state</em>, and session state is only reliable when
 * a client connection maps to one server connection.
 *
 * <p>It did not, in production. Flyway failed with {@code permission denied for table
 * flyway_schema_history}: a message only a {@code salary_app} connection can produce, on a pool
 * that is configured never to be one. The configuration was right and the connection was not,
 * which is what a transaction pooler does - it hands one server connection between clients, and
 * the role set by one arrives with the next. Neon's pooled endpoint is PgBouncer in transaction
 * mode, and documents session settings as unsupported for exactly this reason.
 *
 * <p>So this runs after migration and asks both pools who they are. When the answer is wrong the
 * application refuses to start, naming the likely cause, rather than failing later somewhere that
 * reads as a grant mistake.
 */
@Component
class DatabaseIdentityCheck implements ApplicationRunner {

    static final String APPLICATION_ROLE = "salary_app";

    private static final Logger LOG = LoggerFactory.getLogger(DatabaseIdentityCheck.class);

    private final DataSource applicationPool;
    private final DataSource migrationPool;
    private final String connectionInitSql;

    DatabaseIdentityCheck(
            DataSource applicationPool,
            @FlywayDataSource DataSource migrationPool,
            @Value("${spring.datasource.hikari.connection-init-sql:}") String connectionInitSql) {
        this.applicationPool = applicationPool;
        this.migrationPool = migrationPool;
        this.connectionInitSql = connectionInitSql;
    }

    /**
     * Whether a JDBC URL names a connection pooler, in which case one client connection is not
     * one server session and {@code SET ROLE} cannot be relied on.
     */
    static boolean namesAPooler(String jdbcUrl) {
        if (jdbcUrl == null) {
            return false;
        }
        String url = jdbcUrl.toLowerCase(Locale.ROOT);
        return url.contains("-pooler.") || url.contains("pgbouncer");
    }

    @Override
    public void run(ApplicationArguments args) throws SQLException {
        String migrationRole = currentRoleOf(migrationPool);
        String applicationRole = currentRoleOf(applicationPool);

        if (APPLICATION_ROLE.equals(migrationRole)) {
            throw new IllegalStateException(
                    "migrations are running as %s, which cannot read flyway_schema_history or create a table. %s"
                            .formatted(APPLICATION_ROLE, explain()));
        }
        if (!APPLICATION_ROLE.equals(applicationRole)) {
            throw new IllegalStateException(
                    ("the application is connected as %s rather than %s, so the INSERT/SELECT-only grant on "
                                    + "salary_revision binds nothing and the audit log is editable. %s")
                            .formatted(applicationRole, APPLICATION_ROLE, explain()));
        }
        LOG.info("Database identities verified: migrations as {}, application as {}", migrationRole, applicationRole);
    }

    private String explain() throws SQLException {
        if (!setsItsRolePerConnection()) {
            return "The application pool sets no role at all; check spring.datasource.hikari.connection-init-sql.";
        }
        return namesAPooler(urlOf(applicationPool))
                ? "The datasource points at a connection pooler, where SET ROLE leaks between clients "
                        + "and cannot be relied on. Use the direct database endpoint."
                : "The datasource is not a pooler, so this is a configuration fault rather than leaked "
                        + "session state.";
    }

    private boolean setsItsRolePerConnection() {
        // Read from the configuration rather than from the pool: what was asked for is what is
        // wrong or right here, and it is true of every pool implementation.
        return connectionInitSql != null && !connectionInitSql.isBlank();
    }

    private static String urlOf(DataSource pool) throws SQLException {
        try (Connection connection = pool.getConnection()) {
            return connection.getMetaData().getURL();
        }
    }

    private static String currentRoleOf(DataSource pool) throws SQLException {
        try (Connection connection = pool.getConnection();
                var statement = connection.prepareStatement("SELECT current_user");
                var result = statement.executeQuery()) {
            if (!result.next()) {
                throw new IllegalStateException("the database did not say who this connection is");
            }
            return result.getString(1);
        }
    }
}
