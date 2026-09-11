package com.acme.salarymanagement.employee.application.port.in;

/** The directory screen's one question: who works here, filtered, searched, a page at a time. */
public interface ListEmployees {

    EmployeePage list(DirectoryRequest request);

    /** What the filter controls should offer, taken from the people in the directory. */
    DirectoryFilterOptions filterOptions();
}
