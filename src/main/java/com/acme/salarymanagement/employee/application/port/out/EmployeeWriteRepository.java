package com.acme.salarymanagement.employee.application.port.out;

import java.util.List;

import com.acme.salarymanagement.employee.domain.Employee;

/** Storing employees in bulk. Named for what it does, so `analytics` can be forbidden to touch it. */
public interface EmployeeWriteRepository {

    void saveAll(List<Employee> employees);

    void deleteEveryone();
}
