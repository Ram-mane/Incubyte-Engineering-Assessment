package com.acme.salarymanagement.employee.application.port.out;

import java.util.List;

import com.acme.salarymanagement.employee.domain.Employee;
import com.acme.salarymanagement.employee.domain.SalaryRevision;

/** Storing employees in bulk. Named for what it does, so `analytics` can be forbidden to touch it. */
public interface EmployeeWriteRepository {

    void saveAll(List<Employee> employees);

    /**
     * Bulk-appends revisions the aggregate produced. Same rule as the single-row path: this
     * writes what it is given and has no way to invent one (D113).
     */
    void appendAll(List<SalaryRevision> revisions);

    void deleteEveryone();
}
