package com.acme.salarymanagement.employee.application.port.out;

import java.util.List;

import com.acme.salarymanagement.employee.application.port.in.SalaryRevisionView;
import com.acme.salarymanagement.employee.domain.EmployeeId;

/**
 * Reading the audit log.
 *
 * <p>Separate from {@link SalaryRevisionLog}, which appends: the read side is the one path in this
 * codebase on JPA, and the write side must never be - ADR-0014. A reader that cannot write and a
 * writer that cannot read keeps that line visible in the type system rather than in a comment.
 */
public interface SalaryRevisionReader {

    List<SalaryRevisionView> of(EmployeeId employeeId, int limit);
}
