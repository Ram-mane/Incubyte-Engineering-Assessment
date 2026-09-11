package com.acme.salarymanagement.employee.adapter.out.persistence;

import static com.acme.salarymanagement.employee.application.port.in.DirectoryFilters.none;
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

import com.acme.salarymanagement.employee.application.port.in.DirectoryCursor;
import com.acme.salarymanagement.employee.application.port.in.DirectoryFilters;
import com.acme.salarymanagement.employee.application.port.out.EmployeeDirectoryRepository;
import com.acme.salarymanagement.employee.application.port.out.EmployeeSummary;
import com.acme.salarymanagement.shared.CountryCode;
import com.acme.salarymanagement.shared.CurrencyCode;
import com.acme.salarymanagement.shared.Department;
import com.acme.salarymanagement.shared.JobTitle;
import com.acme.salarymanagement.shared.Money;
import com.acme.salarymanagement.shared.SeniorityLevel;
import com.acme.salarymanagement.support.CountingDataSource;
import com.acme.salarymanagement.support.PostgresIntegrationTest;

/**
 * The directory query against a real PostgreSQL: what comes back, in what order, filtered by what,
 * and at what cost.
 *
 * <p>Written against a database that already has people in it, because it does. The container is
 * shared by the suite and the application role cannot delete a row, so this test cannot empty the
 * table and must not assume anyone else has: it asserts about its own people, found by a family
 * name nothing else generates.
 */
@Transactional
class EmployeeDirectoryIT extends PostgresIntegrationTest {

    /** Sorts after anything the seed generates, so this test's people are a contiguous block. */
    private static final String OURS = "Zzzstaff";

    private static final CurrencyCode INR = new CurrencyCode("INR");

    @Autowired
    private EmployeeDirectoryRepository directory;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void fivePeopleJoinTheDirectory() {
        insert("ZZ-001", "Alice", OURS, "IN", "Engineering", "Software Engineer", "SENIOR", "1200000.0000");
        insert("ZZ-002", "Bhavna", OURS, "DE", "Engineering", "Software Engineer", "MID", "85000.0000");
        insert("ZZ-003", "Chandra", OURS, "IN", "Finance", "Accountant", "SENIOR", "1500000.0000");
        insert("ZZ-004", "Deepa", OURS, "IN", "Engineering", "Data Engineer", "JUNIOR", "800000.0000");
        insert("ZZ-005", "Esha", OURS, "US", "Sales", "Account Executive", "LEAD", "190000.0000");
    }

    @Test
    void the_directory_is_ordered_by_family_name_then_given_name() {
        assertThat(ours(directory.findPage(none(), OURS, null, 50)))
                .extracting(EmployeeSummary::givenName)
                .containsExactly("Alice", "Bhavna", "Chandra", "Deepa", "Esha");
    }

    @Test
    void a_page_resumes_exactly_where_the_cursor_says_it_stopped() {
        var firstTwo = directory.findPage(none(), OURS, null, 2);
        var after = cursorAfter(firstTwo);

        var nextTwo = directory.findPage(none(), OURS, after, 2);

        assertThat(firstTwo).extracting(EmployeeSummary::givenName).containsExactly("Alice", "Bhavna");
        assertThat(nextTwo)
                .as("the row after the cursor, not the row at an offset someone counted to")
                .extracting(EmployeeSummary::givenName)
                .containsExactly("Chandra", "Deepa");
    }

    @Test
    void someone_joining_mid_paging_does_not_push_anyone_onto_a_page_they_were_already_shown() {
        var firstTwo = directory.findPage(none(), OURS, null, 2);
        var after = cursorAfter(firstTwo);

        // Alison sorts before both people already shown. With OFFSET 2 the next page would start
        // at Bhavna and show her twice; keyset names a place, so it cannot.
        insert("ZZ-000", "Alison", OURS, "IN", "Engineering", "Software Engineer", "MID", "990000.0000");

        assertThat(directory.findPage(none(), OURS, after, 2))
                .extracting(EmployeeSummary::givenName)
                .containsExactly("Chandra", "Deepa")
                .doesNotContain("Bhavna");
    }

    @Test
    void the_last_page_simply_runs_out() {
        var page = directory.findPage(none(), OURS, cursorAfter(directory.findPage(none(), OURS, null, 4)), 50);

        assertThat(page).extracting(EmployeeSummary::givenName).containsExactly("Esha");
    }

    @Test
    void filtering_by_country_returns_only_that_market() {
        var filters = new DirectoryFilters(new CountryCode("IN"), null, null, null);

        assertThat(ours(directory.findPage(filters, OURS, null, 50)))
                .extracting(EmployeeSummary::givenName)
                .containsExactly("Alice", "Chandra", "Deepa");
    }

    @Test
    void filtering_by_department_returns_only_that_department() {
        var filters = new DirectoryFilters(null, new Department("Engineering"), null, null);

        assertThat(ours(directory.findPage(filters, OURS, null, 50)))
                .extracting(EmployeeSummary::givenName)
                .containsExactly("Alice", "Bhavna", "Deepa");
    }

    @Test
    void filtering_by_job_title_returns_only_that_role() {
        var filters = new DirectoryFilters(null, null, new JobTitle("Software Engineer"), null);

        assertThat(ours(directory.findPage(filters, OURS, null, 50)))
                .extracting(EmployeeSummary::givenName)
                .containsExactly("Alice", "Bhavna");
    }

    @Test
    void filtering_by_seniority_returns_only_that_level() {
        var filters = new DirectoryFilters(null, null, null, SeniorityLevel.SENIOR);

        assertThat(ours(directory.findPage(filters, OURS, null, 50)))
                .extracting(EmployeeSummary::givenName)
                .containsExactly("Alice", "Chandra");
    }

    @Test
    void filters_narrow_each_other_rather_than_widening() {
        var filters =
                new DirectoryFilters(new CountryCode("IN"), new Department("Engineering"), null, SeniorityLevel.SENIOR);

        assertThat(ours(directory.findPage(filters, OURS, null, 50)))
                .extracting(EmployeeSummary::givenName)
                .containsExactly("Alice");
    }

    @Test
    void search_matches_part_of_a_name_anywhere_in_it() {
        assertThat(ours(directory.findPage(none(), "handr", null, 50)))
                .as("HR managers search for the fragment they remember, not the prefix")
                .extracting(EmployeeSummary::givenName)
                .containsExactly("Chandra");
    }

    @Test
    void search_ignores_case() {
        assertThat(ours(directory.findPage(none(), "ALICE", null, 50)))
                .extracting(EmployeeSummary::givenName)
                .containsExactly("Alice");
    }

    @Test
    void search_matches_an_email_as_well_as_a_name() {
        assertThat(ours(directory.findPage(none(), "zz-005@", null, 50)))
                .extracting(EmployeeSummary::givenName)
                .containsExactly("Esha");
    }

    @Test
    void a_search_term_with_sql_in_it_is_a_search_term() {
        // Parameterised, so this is a string nobody matches rather than a statement anybody runs.
        assertThat(directory.findPage(none(), "'; DROP TABLE employee; --", null, 50))
                .isEmpty();
        assertThat(directory.count(none(), null)).isPositive();
    }

    @Test
    void an_employee_keeps_their_own_currency_and_exact_amount() {
        var alice = ours(directory.findPage(none(), OURS, null, 50)).get(0);

        assertThat(alice.salary())
                .as("salaries are read in the currency they are paid in, never converted on the way out")
                .isEqualTo(Money.of("1200000.0000", INR));
    }

    @Test
    void the_count_is_of_everyone_matching_not_of_the_page() {
        assertThat(directory.count(none(), OURS)).isEqualTo(5);
        assertThat(directory.count(new DirectoryFilters(new CountryCode("IN"), null, null, null), OURS))
                .isEqualTo(3);
    }

    @Test
    void a_page_costs_one_statement_however_many_people_are_on_it() {
        long statements = CountingDataSource.statementsIssuedBy(() -> directory.findPage(none(), null, null, 50));

        // The number that must not grow with the page. A mapper that fetched a department name, a
        // band or a revision per row would read identically and cost fifty-one.
        assertThat(statements)
                .as("one page, one query - whatever the page size")
                .isEqualTo(1);
    }

    @Test
    void filtering_and_searching_do_not_add_statements() {
        var filters = new DirectoryFilters(new CountryCode("IN"), new Department("Engineering"), null, null);

        long statements = CountingDataSource.statementsIssuedBy(() -> directory.findPage(filters, "ali", null, 50));

        assertThat(statements)
                .as("every filter is a branch in one statement, not a query of its own")
                .isEqualTo(1);
    }

    private static List<EmployeeSummary> ours(List<EmployeeSummary> page) {
        return page.stream()
                .filter(summary -> summary.familyName().equals(OURS))
                .toList();
    }

    private static DirectoryCursor cursorAfter(List<EmployeeSummary> page) {
        EmployeeSummary last = page.get(page.size() - 1);
        return new DirectoryCursor(last.familyName(), last.givenName(), last.id());
    }

    private void insert(
            String number,
            String given,
            String family,
            String country,
            String department,
            String title,
            String level,
            String salary) {
        jdbc.update(
                """
                INSERT INTO employee (id, employee_number, given_name, family_name, email, country_code,
                                      department, job_title, seniority_level, hire_date, status,
                                      salary_amount, salary_currency)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, DATE '2024-04-01', 'ACTIVE', ?, ?)
                """,
                UUID.randomUUID(),
                number,
                given,
                family,
                number.toLowerCase(Locale.ROOT) + "@acme.example",
                country,
                department,
                title,
                level,
                new BigDecimal(salary),
                new CountryCode(country).currency().code());
    }
}
