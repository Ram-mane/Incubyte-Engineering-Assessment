package com.acme.salarymanagement.employee.application.port.in;

import java.time.Instant;

import com.acme.salarymanagement.employee.domain.ChangeReason;
import com.acme.salarymanagement.employee.domain.UserId;
import com.acme.salarymanagement.shared.Money;

/**
 * One entry of an employee's pay history, as the screen shows it.
 *
 * @param changedByEmail who made the change, for a person to read. The id answers "which user"; a
 *     UUID on screen answers nobody's question about who raised this salary.
 */
public record SalaryRevisionView(
        Money previousAmount,
        Money newAmount,
        ChangeReason reason,
        UserId changedBy,
        String changedByEmail,
        Instant changedAt,
        String note) {}
