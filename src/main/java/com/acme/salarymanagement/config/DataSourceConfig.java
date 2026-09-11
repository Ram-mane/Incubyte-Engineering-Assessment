package com.acme.salarymanagement.config;

import javax.sql.DataSource;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.flyway.FlywayDataSource;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.autoconfigure.jdbc.JdbcConnectionDetails;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import com.zaxxer.hikari.HikariDataSource;

/**
 * Two pools, because migrating and running want different privileges.
 *
 * <p>The application pool runs {@code SET ROLE salary_app} on every connection
 * (application.yml), which is what makes the INSERT/SELECT-only grant on {@code salary_revision}
 * bind the running system rather than describe a role nothing logs in as. Migrations cannot run
 * that way: on a fresh database the role does not exist until V3 creates it, so a pool that set it
 * could not open its first connection, and a migration creating a table as {@code salary_app}
 * would be denied.
 *
 * <p>Both are declared here rather than one being auto-configured, because declaring any
 * {@code DataSource} bean backs off Spring Boot's - which silently left the whole application on
 * the migration pool, with the restriction applied to nothing.
 */
@Configuration
class DataSourceConfig {

    /** What the application runs on: restricted, via {@code spring.datasource.hikari}. */
    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.hikari")
    HikariDataSource dataSource(
            DataSourceProperties properties, ObjectProvider<JdbcConnectionDetails> connectionDetails) {
        return pool(properties, connectionDetails.getIfAvailable());
    }

    /** What Flyway runs on: the login user, unrestricted, no init SQL. */
    @Bean
    @FlywayDataSource
    DataSource flywayDataSource(
            DataSourceProperties properties, ObjectProvider<JdbcConnectionDetails> connectionDetails) {
        HikariDataSource dataSource = pool(properties, connectionDetails.getIfAvailable());
        // Migrations run once at startup, on two connections: Flyway holds one for the lock on
        // the schema history table while a second applies the migration.
        dataSource.setMaximumPoolSize(2);
        dataSource.setPoolName("flyway");
        return dataSource;
    }

    private static HikariDataSource pool(DataSourceProperties properties, JdbcConnectionDetails details) {
        return DataSourceBuilder.create()
                .type(HikariDataSource.class)
                .url(details != null ? details.getJdbcUrl() : properties.determineUrl())
                .username(details != null ? details.getUsername() : properties.determineUsername())
                .password(details != null ? details.getPassword() : properties.determinePassword())
                .build();
    }
}
