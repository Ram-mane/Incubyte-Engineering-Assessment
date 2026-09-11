package com.acme.salarymanagement.employee.application.service;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.acme.salarymanagement.employee.application.port.in.DirectoryCursor;
import com.acme.salarymanagement.employee.application.port.in.DirectoryFilterOptions;
import com.acme.salarymanagement.employee.application.port.in.DirectoryRequest;
import com.acme.salarymanagement.employee.application.port.in.EmployeePage;
import com.acme.salarymanagement.employee.application.port.in.ListEmployees;
import com.acme.salarymanagement.employee.application.port.out.EmployeeDirectoryRepository;
import com.acme.salarymanagement.employee.application.port.out.EmployeeSummary;
import com.acme.salarymanagement.employee.application.port.out.FilterableColumn;
import com.acme.salarymanagement.shared.SeniorityLevel;

@Service
class ListEmployeesService implements ListEmployees {

    /**
     * A page is capped rather than trusted. {@code ?limit=100000} is one request that returns the
     * whole table, which is the thing pagination exists to prevent.
     */
    static final int LARGEST_PAGE = 200;

    private static final int SMALLEST_PAGE = 1;

    private final EmployeeDirectoryRepository directory;

    ListEmployeesService(EmployeeDirectoryRepository directory) {
        this.directory = directory;
    }

    @Override
    @PreAuthorize("hasAnyRole('HR_MANAGER','HR_ANALYST')")
    public EmployeePage list(DirectoryRequest request) {
        if (request.limit() < SMALLEST_PAGE) {
            throw new IllegalArgumentException("a page holds at least one employee");
        }
        int limit = Math.min(request.limit(), LARGEST_PAGE);

        // One more than asked for. If it comes back, there is another page - which is cheaper and
        // more honest than counting rows to work out whether to offer a "next".
        List<EmployeeSummary> found =
                directory.findPage(request.filters(), request.search(), request.after(), limit + 1);
        boolean more = found.size() > limit;
        List<EmployeeSummary> employees = more ? found.subList(0, limit) : found;

        return new EmployeePage(employees, more ? cursorAfter(employees) : null, totalFor(request));
    }

    @Override
    @PreAuthorize("hasAnyRole('HR_MANAGER','HR_ANALYST')")
    public DirectoryFilterOptions filterOptions() {
        return new DirectoryFilterOptions(
                directory.distinctValuesOf(FilterableColumn.COUNTRY),
                directory.distinctValuesOf(FilterableColumn.DEPARTMENT),
                directory.distinctValuesOf(FilterableColumn.JOB_TITLE),
                // Not from the data: a level nobody currently holds is still a level, and the
                // list of them belongs to the domain rather than to whoever happens to be hired.
                List.of(SeniorityLevel.values()));
    }

    private static String cursorAfter(List<EmployeeSummary> employees) {
        EmployeeSummary last = employees.get(employees.size() - 1);
        return new DirectoryCursor(last.familyName(), last.givenName(), last.id()).encoded();
    }

    /**
     * Counted on the first page and not on any page after it.
     *
     * <p>ADR-0004's objection to {@code COUNT(*)} is that it runs on <em>every</em> page; a screen
     * still has to say how large the result is, and at ten thousand rows one filtered count off
     * the same indexes is a few milliseconds. The field is named {@code totalApprox} because that
     * freedom is the contract: when the table is large enough for the count to hurt, it becomes an
     * estimate from {@code pg_class.reltuples} without any client changing
     * (06-SCALABILITY.md states the threshold).
     */
    private Long totalFor(DirectoryRequest request) {
        return request.isFirstPage() ? directory.count(request.filters(), request.search()) : null;
    }
}
