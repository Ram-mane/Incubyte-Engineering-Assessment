package com.acme.salarymanagement.employee.application.port.in;

import java.util.List;

import com.acme.salarymanagement.employee.domain.Employee;

/** Bulk arrival of employees: seeding today, CSV import at 2.x. */
public interface StoreEmployees {

    void replaceEveryoneWith(List<Employee> employees);
}
