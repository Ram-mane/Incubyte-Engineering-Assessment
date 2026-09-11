package com.acme.salarymanagement.employee.application.port.in;

import com.acme.salarymanagement.employee.domain.ChangeReason;
import com.acme.salarymanagement.employee.domain.EmployeeId;
import com.acme.salarymanagement.employee.domain.UserId;
import com.acme.salarymanagement.shared.Money;

/**
 * A request to move someone's pay.
 *
 * @param actor who is making the change. Mandatory, and checked against the user table: an audit
 *     record whose author is a guess is not an audit record. Until authentication lands it comes
 *     from the request, which is why it is validated rather than trusted.
 * @param note free text; optional, unlike the reason
 */
public record ChangeSalaryCommand(
        EmployeeId employeeId, Money newSalary, ChangeReason reason, UserId actor, String note) {}
