package com.acme.salarymanagement.employee.adapter.in.seed;

import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.acme.salarymanagement.employee.application.port.in.StoreEmployees;
import com.acme.salarymanagement.employee.domain.UserId;

/**
 * {@code mvn spring-boot:run -Dspring-boot.run.profiles=seed} - fills an empty database with an
 * org to look at, then serves it.
 *
 * <p>Replaces rather than appends: a seed is a known world, not an increment, so running it twice
 * leaves exactly the same ten thousand people. The log records how many and how long, and no
 * salary figure, because a seeding log is still a log.
 */
@Component
@Profile("seed")
@Order(2)
class EmployeeSeedRunner implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(EmployeeSeedRunner.class);

    /** Matches DemoUserSeedRunner's fixed id, so a seeded revision names a user that exists. */
    private static final java.util.UUID SEEDED_MANAGER =
            java.util.UUID.fromString("00000000-0000-4000-8000-00000000ffff");

    private final StoreEmployees employees;
    private final int size;

    EmployeeSeedRunner(StoreEmployees employees, @Value("${seed.employees:10000}") int size) {
        this.employees = employees;
        this.size = size;
    }

    @Override
    public void run(ApplicationArguments args) {
        seed(size);
    }

    void seed(int count) {
        Instant startedAt = Instant.now();
        // Credited to the seeded HR manager, who exists because DemoUserSeedRunner runs first:
        // changed_by is NOT NULL with a foreign key, and there is no system user to invent.
        EmployeePopulation.Org org = EmployeePopulation.withHistory(count, new UserId(SEEDED_MANAGER));
        employees.replaceEveryoneWith(org.employees(), org.revisions());
        long tookMillis = Duration.between(startedAt, Instant.now()).toMillis();
        int recorded = org.revisions().size();
        LOG.info("Seeded {} employees and {} revisions in {} ms", count, recorded, tookMillis);
    }
}
