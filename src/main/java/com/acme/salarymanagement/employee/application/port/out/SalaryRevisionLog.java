package com.acme.salarymanagement.employee.application.port.out;

import java.util.List;

import com.acme.salarymanagement.employee.application.port.in.SalaryRevisionView;
import com.acme.salarymanagement.employee.domain.EmployeeId;
import com.acme.salarymanagement.employee.domain.SalaryRevision;

/**
 * The append-only log. Insert and read, because that is all the database grants it (D020).
 *
 * <p>{@link #append} takes the revision the aggregate produced. It does not take an employee and
 * a pair of amounts: a log that can be written from before-and-after state is a log that can be
 * written by any code path that changes pay, and the point of {@code changeSalaryTo} returning
 * the revision is that there is no such path.
 */
public interface SalaryRevisionLog {

    void append(SalaryRevision revision);

    List<SalaryRevisionView> of(EmployeeId employeeId, int limit);
}
