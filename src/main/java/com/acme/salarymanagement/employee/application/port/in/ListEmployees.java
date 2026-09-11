package com.acme.salarymanagement.employee.application.port.in;

/** The directory screen's one question: who works here, fifty at a time. */
public interface ListEmployees {

    EmployeePage list(int page, int size);
}
