package com.acme.salarymanagement.employee.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.acme.salarymanagement.employee.application.port.in.DirectoryCursor;
import com.acme.salarymanagement.employee.application.port.in.DirectoryFilters;
import com.acme.salarymanagement.employee.application.port.in.DirectoryRequest;
import com.acme.salarymanagement.employee.application.port.out.EmployeeDirectoryRepository;
import com.acme.salarymanagement.employee.application.port.out.EmployeeSummary;
import com.acme.salarymanagement.shared.CountryCode;
import com.acme.salarymanagement.shared.CurrencyCode;
import com.acme.salarymanagement.shared.Department;
import com.acme.salarymanagement.shared.JobTitle;
import com.acme.salarymanagement.shared.Money;
import com.acme.salarymanagement.shared.SeniorityLevel;

/**
 * What the screen is handed, against a repository that records what it was asked for. Whether the
 * SQL is right is {@code EmployeeDirectoryIT}'s job; this is about the page contract.
 */
class ListEmployeesServiceTest {

    private final RecordingDirectory directory = new RecordingDirectory();
    private final ListEmployeesService service = new ListEmployeesService(directory);

    private static DirectoryRequest firstPageOf(int limit) {
        return new DirectoryRequest(null, DirectoryFilters.none(), null, limit);
    }

    @Test
    void a_full_page_offers_a_cursor_to_the_next_one() {
        directory.holding(51);

        var page = service.list(firstPageOf(50));

        assertThat(page.employees()).hasSize(50);
        assertThat(page.nextCursor()).isNotNull();
    }

    @Test
    void the_cursor_names_the_last_person_on_the_page_not_the_first_of_the_next() {
        directory.holding(51);

        var page = service.list(firstPageOf(50));
        var cursor = DirectoryCursor.decode(page.nextCursor());
        var last = page.employees().get(49);

        assertThat(cursor.id()).isEqualTo(last.id());
        assertThat(cursor.familyName()).isEqualTo(last.familyName());
    }

    @Test
    void the_last_page_offers_no_cursor_at_all() {
        directory.holding(30);

        var page = service.list(firstPageOf(50));

        assertThat(page.employees()).hasSize(30);
        assertThat(page.nextCursor())
                .as("a cursor on the last page invites one more request that returns nothing")
                .isNull();
    }

    @Test
    void a_page_exactly_filling_the_limit_does_not_claim_another_page() {
        directory.holding(50);

        var page = service.list(firstPageOf(50));

        // The boundary this gets wrong when written with count(): fifty rows and no fifty-first.
        assertThat(page.employees()).hasSize(50);
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    void one_more_row_than_asked_for_is_fetched_to_learn_whether_there_is_a_next_page() {
        directory.holding(100);

        service.list(firstPageOf(50));

        assertThat(directory.limitAsked)
                .as("cheaper and more honest than counting rows to decide whether to offer a next")
                .isEqualTo(51);
    }

    @Test
    void the_total_is_counted_on_the_first_page() {
        directory.holding(100);

        assertThat(service.list(firstPageOf(50)).totalApprox()).isEqualTo(RecordingDirectory.MATCHING);
    }

    @Test
    void the_total_is_not_recounted_on_every_page() {
        directory.holding(100);
        var cursor = new DirectoryCursor("Kapoor", "Alice", UUID.randomUUID());

        var page = service.list(new DirectoryRequest(null, DirectoryFilters.none(), cursor, 50));

        assertThat(page.totalApprox())
                .as("ADR-0004's objection to COUNT(*) is that it runs on every page")
                .isNull();
        assertThat(directory.counted).isFalse();
    }

    @Test
    void a_page_larger_than_the_maximum_is_capped() {
        directory.holding(1_000);

        var page = service.list(firstPageOf(100_000));

        assertThat(page.employees()).hasSize(ListEmployeesService.LARGEST_PAGE);
    }

    @Test
    void a_page_of_nobody_is_rejected() {
        assertThatThrownBy(() -> service.list(firstPageOf(0))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void the_filters_and_the_search_reach_the_query_unchanged() {
        directory.holding(1);
        var filters = new DirectoryFilters(
                new CountryCode("DE"),
                new Department("Engineering"),
                new JobTitle("Software Engineer"),
                SeniorityLevel.SENIOR);

        service.list(new DirectoryRequest("rao", filters, null, 50));

        assertThat(directory.filtersAsked).isEqualTo(filters);
        assertThat(directory.searchAsked).isEqualTo("rao");
    }

    private static final class RecordingDirectory implements EmployeeDirectoryRepository {

        static final long MATCHING = 10_000L;

        private List<EmployeeSummary> available = List.of();
        private int limitAsked;
        private DirectoryFilters filtersAsked;
        private String searchAsked;
        private boolean counted;

        void holding(int people) {
            var employees = new ArrayList<EmployeeSummary>();
            IntStream.range(0, people)
                    .forEach(index -> employees.add(new EmployeeSummary(
                            UUID.nameUUIDFromBytes(
                                    Integer.toString(index).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                            "E%05d".formatted(index),
                            "Given" + index,
                            "Family" + index,
                            "person%d@acme.example".formatted(index),
                            new CountryCode("IN"),
                            new Department("Engineering"),
                            new JobTitle("Software Engineer"),
                            SeniorityLevel.SENIOR,
                            Money.of(new BigDecimal("1200000.00"), new CurrencyCode("INR")))));
            available = employees;
        }

        @Override
        public List<EmployeeSummary> findPage(
                DirectoryFilters filters, String search, DirectoryCursor after, int limit) {
            this.filtersAsked = filters;
            this.searchAsked = search;
            this.limitAsked = limit;
            return available.subList(0, Math.min(limit, available.size()));
        }

        @Override
        public long count(DirectoryFilters filters, String search) {
            this.counted = true;
            return MATCHING;
        }

        @Override
        public List<String> distinctValuesOf(
                com.acme.salarymanagement.employee.application.port.out.FilterableColumn column) {
            return List.of(column.name());
        }
    }
}
