package com.acme.salarymanagement.employee.application.service;

import org.springframework.stereotype.Service;

import com.acme.salarymanagement.employee.application.port.in.EmployeePage;
import com.acme.salarymanagement.employee.application.port.in.ListEmployees;
import com.acme.salarymanagement.employee.application.port.out.EmployeeDirectoryRepository;

@Service
class ListEmployeesService implements ListEmployees {

    /**
     * A page is capped rather than trusted. {@code ?size=100000} is one request that returns the
     * whole table, which is the thing pagination exists to prevent.
     */
    static final int LARGEST_PAGE = 200;

    private static final int SMALLEST_PAGE = 1;

    private final EmployeeDirectoryRepository directory;

    ListEmployeesService(EmployeeDirectoryRepository directory) {
        this.directory = directory;
    }

    @Override
    public EmployeePage list(int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("a page number cannot be negative");
        }
        if (size < SMALLEST_PAGE) {
            throw new IllegalArgumentException("a page holds at least one employee");
        }
        int pageSize = Math.min(size, LARGEST_PAGE);
        // Offset is a long because page is an int the caller chooses: page 50,000,000 at size 200
        // overflows an int multiplication and would quietly return the first page instead.
        long offset = (long) page * pageSize;
        return new EmployeePage(directory.findPage(offset, pageSize), page, pageSize, directory.count());
    }
}
