package com.acme.salarymanagement.employee.application.port.in;

import com.acme.salarymanagement.employee.application.port.out.EmployeeSummary;

/** The only way pay changes from outside the domain. */
public interface ChangeSalary {

    /**
     * Applies the change and records it, in one transaction.
     *
     * @return the employee as they now are
     */
    EmployeeSummary change(ChangeSalaryCommand command);
}
