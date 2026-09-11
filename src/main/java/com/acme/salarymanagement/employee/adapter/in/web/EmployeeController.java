package com.acme.salarymanagement.employee.adapter.in.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.acme.salarymanagement.employee.application.port.in.DirectoryCursor;
import com.acme.salarymanagement.employee.application.port.in.DirectoryFilterOptions;
import com.acme.salarymanagement.employee.application.port.in.DirectoryFilters;
import com.acme.salarymanagement.employee.application.port.in.DirectoryRequest;
import com.acme.salarymanagement.employee.application.port.in.GetEmployee;
import com.acme.salarymanagement.employee.application.port.in.ListEmployees;
import com.acme.salarymanagement.employee.domain.EmployeeId;
import com.acme.salarymanagement.shared.CountryCode;
import com.acme.salarymanagement.shared.Department;
import com.acme.salarymanagement.shared.JobTitle;
import com.acme.salarymanagement.shared.SeniorityLevel;

/**
 * The directory endpoint: filtered, searched, keyset-paged. Authentication is 2.9.
 *
 * <p>Each filter is parsed into its own type here, at the edge, so {@code ?country=XX} is refused
 * as a bad request rather than travelling inward as a string that matches nobody.
 */
@RestController
@RequestMapping("/api/v1/employees")
class EmployeeController {

    private static final int DEFAULT_LIMIT = 50;

    private final ListEmployees listEmployees;
    private final GetEmployee getEmployee;

    EmployeeController(ListEmployees listEmployees, GetEmployee getEmployee) {
        this.listEmployees = listEmployees;
        this.getEmployee = getEmployee;
    }

    @GetMapping
    EmployeePageResponse list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String jobTitle,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "" + DEFAULT_LIMIT) int limit) {

        DirectoryFilters filters = new DirectoryFilters(
                country == null ? null : new CountryCode(country),
                department == null ? null : new Department(department),
                jobTitle == null ? null : new JobTitle(jobTitle),
                level == null ? null : SeniorityLevel.valueOf(level.toUpperCase(java.util.Locale.ROOT)));

        DirectoryRequest request =
                new DirectoryRequest(q, filters, cursor == null ? null : DirectoryCursor.decode(cursor), limit);

        return EmployeePageResponse.of(listEmployees.list(request));
    }

    /** One person. The detail screen reads this and their pay history side by side. */
    @GetMapping("/{id}")
    EmployeeResponse byId(@PathVariable java.util.UUID id) {
        return EmployeeResponse.of(getEmployee.byId(new EmployeeId(id)));
    }

    /** What the filter controls should offer. Derived from the directory, not configured. */
    @GetMapping("/filter-options")
    DirectoryFilterOptions filterOptions() {
        return listEmployees.filterOptions();
    }
}
