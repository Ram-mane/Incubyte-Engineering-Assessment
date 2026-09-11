package com.acme.salarymanagement.employee.application.port.in;

import java.time.Instant;

import com.acme.salarymanagement.employee.domain.ChangeReason;
import com.acme.salarymanagement.employee.domain.UserId;
import com.acme.salarymanagement.shared.Money;

/** One entry of an employee's pay history, as the screen shows it. */
public record SalaryRevisionView(
        Money previousAmount, Money newAmount, ChangeReason reason, UserId changedBy, Instant changedAt, String note) {}
