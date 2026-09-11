package com.acme.salarymanagement.employee.application.port.in;

import java.util.List;

import com.acme.salarymanagement.employee.domain.EmployeeId;

/** One employee's audit log, newest first. */
public interface GetSalaryRevisions {

    List<SalaryRevisionView> of(EmployeeId employeeId, int limit);
}
