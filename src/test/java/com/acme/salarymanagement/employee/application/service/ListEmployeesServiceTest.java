package com.acme.salarymanagement.employee.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.acme.salarymanagement.employee.application.port.out.EmployeeDirectoryRepository;
import com.acme.salarymanagement.employee.application.port.out.EmployeeSummary;
import com.acme.salarymanagement.shared.CountryCode;
import com.acme.salarymanagement.shared.CurrencyCode;
import com.acme.salarymanagement.shared.Department;
import com.acme.salarymanagement.shared.JobTitle;
import com.acme.salarymanagement.shared.Money;
import com.acme.salarymanagement.shared.SeniorityLevel;

/**
 * Paging arithmetic, against a repository that records what it was asked for. The database is not
 * the thing under test here - which page of which size the screen asked for is.
 */
class ListEmployeesServiceTest {

    private final RecordingDirectory directory = new RecordingDirectory();
    private final ListEmployeesService service = new ListEmployeesService(directory);

    @Test
    void the_first_page_starts_at_the_beginning() {
        service.list(0, 50);

        assertThat(directory.offset).isZero();
        assertThat(directory.limit).isEqualTo(50);
    }

    @Test
    void the_second_page_starts_where_the_first_ended() {
        service.list(1, 50);

        assertThat(directory.offset).isEqualTo(50);
    }

    @Test
    void a_page_larger_than_the_maximum_is_capped() {
        var page = service.list(0, 100_000);

        assertThat(directory.limit)
                .as("one request returning the whole table is what pagination exists to prevent")
                .isEqualTo(ListEmployeesService.LARGEST_PAGE);
        assertThat(page.size()).isEqualTo(ListEmployeesService.LARGEST_PAGE);
    }

    @Test
    void a_deep_page_does_not_wrap_around_to_the_first() {
        // page * size overflows an int well before it overflows a long, and an overflowed offset
        // silently returns the wrong page rather than failing.
        service.list(50_000_000, 200);

        assertThat(directory.offset).isEqualTo(10_000_000_000L);
    }

    @Test
    void a_negative_page_is_rejected() {
        assertThatThrownBy(() -> service.list(-1, 50)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void a_page_of_nobody_is_rejected() {
        assertThatThrownBy(() -> service.list(0, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void the_page_carries_the_total_so_a_pager_can_render_itself() {
        var page = service.list(0, 50);

        assertThat(page.total()).isEqualTo(RecordingDirectory.TOTAL);
        assertThat(page.employees()).hasSize(1);
    }

    private static final class RecordingDirectory implements EmployeeDirectoryRepository {

        static final long TOTAL = 10_000L;

        private long offset;
        private int limit;

        @Override
        public List<EmployeeSummary> findPage(long offset, int limit) {
            this.offset = offset;
            this.limit = limit;
            return List.of(new EmployeeSummary(
                    UUID.fromString("11111111-1111-1111-1111-111111111111"),
                    "E00042",
                    "Alice",
                    "Kapoor",
                    "alice.kapoor@acme.test",
                    new CountryCode("IN"),
                    new Department("Engineering"),
                    new JobTitle("Software Engineer"),
                    SeniorityLevel.SENIOR,
                    Money.of(new BigDecimal("1200000.00"), new CurrencyCode("INR"))));
        }

        @Override
        public long count() {
            return TOTAL;
        }
    }
}
