package com.acme.salarymanagement.support;

import java.sql.Connection;
import java.sql.SQLException;

import org.testcontainers.containers.PostgreSQLContainer;

/**
 * A connection as the database owner, for the few tests that must set up what the application is
 * deliberately not allowed to write.
 *
 * <p>The application role holds SELECT only on {@code exchange_rate} and {@code salary_band}
 * (D103), which is the point - so a test that needs a rate row cannot use the application's
 * template and must not be given permission to. It borrows the container's own credentials
 * instead, exactly as the seed borrows a privileged pool.
 *
 * <p>Anything written this way is committed and visible to every other test in the JVM. Delete it
 * in a finally.
 */
public final class DatabaseOwner {

    private DatabaseOwner() {}

    public static Connection connect() throws SQLException {
        PostgreSQLContainer<?> container = PostgresIntegrationTest.POSTGRES;
        return java.sql.DriverManager.getConnection(
                container.getJdbcUrl(), container.getUsername(), container.getPassword());
    }
}
