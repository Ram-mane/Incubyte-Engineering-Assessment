package com.acme.salarymanagement.employee.adapter.in.web;

import java.time.Instant;
import java.util.UUID;

import com.acme.salarymanagement.employee.application.port.in.SalaryRevisionView;

/** One entry of the audit log as the API renders it. */
record SalaryRevisionResponse(
        EmployeeResponse.MoneyResponse previousAmount,
        EmployeeResponse.MoneyResponse newAmount,
        String reason,
        UUID changedBy,
        String changedByEmail,
        Instant changedAt,
        String note) {

    static SalaryRevisionResponse of(SalaryRevisionView revision) {
        return new SalaryRevisionResponse(
                new EmployeeResponse.MoneyResponse(
                        revision.previousAmount().amount().toPlainString(),
                        revision.previousAmount().currency().code()),
                new EmployeeResponse.MoneyResponse(
                        revision.newAmount().amount().toPlainString(),
                        revision.newAmount().currency().code()),
                revision.reason().name(),
                revision.changedBy().value(),
                revision.changedByEmail(),
                revision.changedAt(),
                revision.note());
    }
}
