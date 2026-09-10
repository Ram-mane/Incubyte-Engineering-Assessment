package com.acme.salarymanagement.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base for tests that need a real database.
 *
 * <p>The container is started once per JVM from a static initialiser rather than by the
 * {@code @Testcontainers} extension, which starts and stops per class and so hands a cached Spring
 * context a database that has already gone away. Flyway migrates it on first context load, so tests
 * see exactly the schema production sees.
 */
@SpringBootTest
public abstract class PostgresIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine").withReuse(true);

    static {
        POSTGRES.start();
    }
}
