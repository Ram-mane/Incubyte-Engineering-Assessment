package com.acme.salarymanagement.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.autoconfigure.jdbc.JdbcConnectionDetails;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;

import com.zaxxer.hikari.HikariDataSource;

/**
 * A deliberate privilege escalation, visible as one bean.
 *
 * <p>Seeding is provisioning, not something the application does. The runtime role is granted
 * {@code SELECT} on reference data and nothing more, and it stays that way: widening a runtime
 * grant so a loading job can write would let the seeder shape the application's privileges, and
 * "read-only" would quietly come to mean "read-only except for the job that writes it" (D103).
 *
 * <p>So the seed profile gets its own pool, without the {@code SET ROLE salary_app} the
 * application runs, and a reviewer can see the escalation and object to it. It exists only under
 * that profile: nothing in a deployed instance can reach it.
 */
@Configuration
@Profile("seed")
class SeedDataSourceConfig {

    @Bean
    JdbcTemplate seedJdbcTemplate(
            DataSourceProperties properties, ObjectProvider<JdbcConnectionDetails> connectionDetails) {
        JdbcConnectionDetails details = connectionDetails.getIfAvailable();
        HikariDataSource dataSource = DataSourceBuilder.create()
                .type(HikariDataSource.class)
                .url(details != null ? details.getJdbcUrl() : properties.determineUrl())
                .username(details != null ? details.getUsername() : properties.determineUsername())
                .password(details != null ? details.getPassword() : properties.determinePassword())
                .build();
        dataSource.setMaximumPoolSize(2);
        dataSource.setPoolName("seed");
        // Ten thousand single-row round trips is the difference between two seconds and two
        // minutes; PostgreSQL folds a batch into multi-row INSERTs only when asked.
        dataSource.addDataSourceProperty("reWriteBatchedInserts", "true");
        return new JdbcTemplate(dataSource);
    }
}
