package com.acme.salarymanagement.employee.adapter.in.seed;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.acme.salarymanagement.employee.application.port.out.EmployeeDirectoryRepository;
import com.acme.salarymanagement.employee.application.port.out.EmployeeWriteRepository;
import com.acme.salarymanagement.support.PostgresIntegrationTest;

/**
 * The seed writes through its own pool, and what it writes is what the directory reads back.
 *
 * <p>A small population here: the point is that the privileged path works end to end, not how
 * fast ten thousand rows go in. The seeding itself happens on context startup, as it does when
 * the profile is run for real.
 */
@ActiveProfiles("seed")
@SpringBootTest(properties = "seed.employees=25")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EmployeeSeedIT extends PostgresIntegrationTest {

    @Autowired
    private EmployeeDirectoryRepository directory;

    @Autowired
    private EmployeeWriteRepository employees;

    /**
     * The container is shared by the whole suite, and these rows are committed rather than rolled
     * back - the seed runs at context startup, outside any test transaction. Leaving twenty-five
     * people behind makes every later test that counts rows depend on which class ran first.
     */
    @AfterAll
    void the_seeded_org_does_not_outlive_this_test() {
        employees.deleteEveryone();
    }

    @Test
    void the_seeded_org_is_there_to_be_read() {
        assertThat(directory.count()).isEqualTo(25);
    }

    @Test
    void a_seeded_employee_arrives_whole() {
        var someone = directory.findPage(0, 1).get(0);

        assertThat(someone.employeeNumber()).startsWith("E");
        assertThat(someone.email()).endsWith("@acme.example");
        assertThat(someone.salary().currency())
                .as("the seed stores local currency; nothing converts on the way in or out")
                .isEqualTo(someone.country().currency());
        assertThat(someone.salary().isPositive()).isTrue();
    }

    @Test
    void seeding_twice_leaves_the_same_org_rather_than_two(@Autowired EmployeeSeedRunner runner) {
        runner.seed(25);

        assertThat(directory.count())
                .as("a seed is a known world, not an increment")
                .isEqualTo(25);
    }
}
