package com.acme.salarymanagement.employee.application.port.in;

import com.acme.salarymanagement.employee.application.port.out.EmployeeSummary;
import com.acme.salarymanagement.employee.domain.EmployeeId;

/** One person, for the screen that shows them and their pay history. */
public interface GetEmployee {

    EmployeeSummary byId(EmployeeId id);
}
