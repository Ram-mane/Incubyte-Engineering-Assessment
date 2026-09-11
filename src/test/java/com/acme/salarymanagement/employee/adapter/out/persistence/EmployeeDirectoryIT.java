package com.acme.salarymanagement.employee.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.acme.salarymanagement.employee.application.port.out.EmployeeDirectoryRepository;
import com.acme.salarymanagement.shared.CurrencyCode;
import com.acme.salarymanagement.shared.Money;
import com.acme.salarymanagement.support.PostgresIntegrationTest;

/** The directory query against a real PostgreSQL: what comes back, in what order, and how much. */
@Transactional
class EmployeeDirectoryIT extends PostgresIntegrationTest {

    @Autowired
    private EmployeeDirectoryRepository directory;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void threePeopleInTheDirectory() {
        insert("E-003", "Chandra", "Rao", "1500000.0000");
        insert("E-001", "Alice", "Kapoor", "1200000.0000");
        insert("E-002", "Bhavna", "Rao", "1380000.0000");
    }

    @Test
    void the_directory_is_ordered_by_family_name_then_given_name() {
        var page = directory.findPage(0, 50);

        assertThat(page)
                .extracting(summary -> summary.familyName() + ", " + summary.givenName())
                .containsExactly("Kapoor, Alice", "Rao, Bhavna", "Rao, Chandra");
    }

    @Test
    void a_page_returns_only_its_own_slice() {
        var second = directory.findPage(1, 1);

        assertThat(second).extracting(summary -> summary.givenName()).containsExactly("Bhavna");
    }

    @Test
    void a_page_past_the_end_is_empty_rather_than_an_error() {
        assertThat(directory.findPage(99, 50)).isEmpty();
    }

    @Test
    void an_employee_keeps_their_own_currency_and_exact_amount() {
        var alice = directory.findPage(0, 1).get(0);

        assertThat(alice.salary())
                .as("salaries are read in the currency they are paid in, never converted on the way out")
                .isEqualTo(Money.of("1200000.0000", new CurrencyCode("INR")));
    }

    @Test
    void the_count_is_of_everyone_not_of_the_page() {
        assertThat(directory.count()).isEqualTo(3);
    }

    private void insert(String number, String given, String family, String salary) {
        jdbc.update(
                """
                INSERT INTO employee (id, employee_number, given_name, family_name, email, country_code,
                                      department, job_title, seniority_level, hire_date, status,
                                      salary_amount, salary_currency)
                VALUES (?, ?, ?, ?, ?, 'IN', 'Engineering', 'Engineer', 'SENIOR',
                        DATE '2024-04-01', 'ACTIVE', ?, 'INR')
                """,
                UUID.randomUUID(),
                number,
                given,
                family,
                number.toLowerCase(java.util.Locale.ROOT) + "@acme.example",
                new java.math.BigDecimal(salary));
    }
}
