package com.acme.salarymanagement.employee.adapter.in.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.acme.salarymanagement.employee.application.port.in.ListEmployees;

/**
 * The directory endpoint. No filters, no search, no auth yet - those are 2.6 and 2.9. What it
 * does have is server-side paging, because the alternative is ten thousand rows on the wire.
 */
@RestController
@RequestMapping("/api/v1/employees")
class EmployeeController {

    private static final int DEFAULT_PAGE_SIZE = 50;

    private final ListEmployees listEmployees;

    EmployeeController(ListEmployees listEmployees) {
        this.listEmployees = listEmployees;
    }

    @GetMapping
    EmployeePageResponse list(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size) {
        return EmployeePageResponse.of(listEmployees.list(page, size));
    }
}
