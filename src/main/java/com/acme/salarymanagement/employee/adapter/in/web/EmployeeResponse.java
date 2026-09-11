package com.acme.salarymanagement.employee.adapter.in.web;

import java.util.UUID;

import com.acme.salarymanagement.employee.application.port.out.EmployeeSummary;

/**
 * One employee as the API renders them.
 *
 * <p>Money is an object with the amount as a <em>string</em>, never a JSON number: a large salary
 * loses precision the moment a JavaScript client parses it as a double, and the currency travels
 * with the amount so no client has to assume one (04-API-DESIGN.md).
 */
record EmployeeResponse(
        UUID id,
        String employeeNumber,
        String givenName,
        String familyName,
        String email,
        String country,
        String department,
        String jobTitle,
        String level,
        MoneyResponse salary) {

    static EmployeeResponse of(EmployeeSummary summary) {
        return new EmployeeResponse(
                summary.id(),
                summary.employeeNumber(),
                summary.givenName(),
                summary.familyName(),
                summary.email(),
                summary.country().code(),
                summary.department().value(),
                summary.jobTitle().value(),
                summary.level().name(),
                new MoneyResponse(
                        summary.salary().amount().toPlainString(),
                        summary.salary().currency().code()));
    }

    record MoneyResponse(String amount, String currency) {}
}
