package com.acme.salarymanagement;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.acme.salarymanagement.support.PostgresIntegrationTest;

class DatabaseMigrationIT extends PostgresIntegrationTest {

    @Test
    void the_trigram_extension_is_installed_by_a_migration(@Autowired DataSource dataSource) throws SQLException {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("SELECT count(*) FROM pg_extension WHERE extname = ?")) {
            statement.setString(1, "pg_trgm");
            try (var results = statement.executeQuery()) {
                assertThat(results.next()).isTrue();
                assertThat(results.getInt(1))
                        .as("pg_trgm must be installed by Flyway, not by hand on each environment")
                        .isEqualTo(1);
            }
        }
    }
}
