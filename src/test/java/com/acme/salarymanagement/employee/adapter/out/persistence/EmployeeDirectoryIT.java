package com.acme.salarymanagement.employee.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.acme.salarymanagement.employee.application.port.out.EmployeeDirectoryRepository;
import com.acme.salarymanagement.employee.application.port.out.EmployeeSummary;
import com.acme.salarymanagement.shared.CurrencyCode;
import com.acme.salarymanagement.shared.Money;
import com.acme.salarymanagement.support.PostgresIntegrationTest;

/**
 * The directory query against a real PostgreSQL: what comes back, in what order, and how much.
 *
 * <p>Written against a database that already has people in it, because it does. The container is
 * shared by the suite and the application role cannot delete a row, so this test cannot empty the
 * table and must not assume anyone else has. Every assertion is therefore about <em>its own</em>
 * three employees, or about a total relative to the count before they arrived - which is also
 * what makes it independent of the order the suite happens to run in.
 */
@Transactional
class EmployeeDirectoryIT extends PostgresIntegrationTest {

    /** Family names chosen to sort after anything the seed generates, so paging is predictable. */
    private static final String OURS = "Zzz";

    @Autowired
    private EmployeeDirectoryRepository directory;

    @Autowired
    private JdbcTemplate jdbc;

    private long othersAlreadyHere;

    @BeforeEach
    void threePeopleJoinTheDirectory() {
        othersAlreadyHere = directory.count();
        insert("ZZ-003", "Chandra", OURS + "arate", "1500000.0000");
        insert("ZZ-001", "Alice", OURS + "aabe", "1200000.0000");
        insert("ZZ-002", "Bhavna", OURS + "arate", "1380000.0000");
    }

    @Test
    void the_directory_is_ordered_by_family_name_then_given_name() {
        assertThat(ourThree())
                .extracting(summary -> summary.familyName() + ", " + summary.givenName())
                .containsExactly(OURS + "aabe, Alice", OURS + "arate, Bhavna", OURS + "arate, Chandra");
    }

    @Test
    void consecutive_pages_do_not_overlap_or_skip_anyone() {
        var everyone = directory.findPage(0, (int) directory.count());
        // However many people are in the table - this test's three, or those plus a seeded org -
        // the second page is the rows the first one did not return.
        int endOfSecondPage = Math.min(4, everyone.size());

        assertThat(directory.findPage(0, 2)).containsExactlyElementsOf(everyone.subList(0, 2));
        assertThat(directory.findPage(2, 2))
                .as("a page boundary is where rows are quietly dropped or shown twice")
                .containsExactlyElementsOf(everyone.subList(2, endOfSecondPage));
    }

    @Test
    void a_page_past_the_end_is_empty_rather_than_an_error() {
        assertThat(directory.findPage(directory.count() + 10, 50)).isEmpty();
    }

    @Test
    void an_employee_keeps_their_own_currency_and_exact_amount() {
        var alice = ourThree().get(0);

        assertThat(alice.salary())
                .as("salaries are read in the currency they are paid in, never converted on the way out")
                .isEqualTo(Money.of("1200000.0000", new CurrencyCode("INR")));
    }

    @Test
    void the_count_is_of_everyone_not_of_the_page() {
        assertThat(directory.findPage(0, 2)).hasSize(2);
        assertThat(directory.count()).isEqualTo(othersAlreadyHere + 3);
    }

    /** The three this test inserted, in the order the directory returns them. */
    private List<EmployeeSummary> ourThree() {
        return directory.findPage(0, (int) directory.count()).stream()
                .filter(summary -> summary.familyName().startsWith(OURS))
                .toList();
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
                number.toLowerCase(Locale.ROOT) + "@acme.example",
                new BigDecimal(salary));
    }
}
