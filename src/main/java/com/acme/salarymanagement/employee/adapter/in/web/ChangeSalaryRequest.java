package com.acme.salarymanagement.employee.adapter.in.web;

import java.math.BigDecimal;
import java.util.UUID;

import com.acme.salarymanagement.employee.application.port.in.ChangeSalaryCommand;
import com.acme.salarymanagement.employee.domain.ChangeReason;
import com.acme.salarymanagement.employee.domain.EmployeeId;
import com.acme.salarymanagement.employee.domain.UserId;
import com.acme.salarymanagement.shared.CurrencyCode;
import com.acme.salarymanagement.shared.Money;

/**
 * A pay change as the API receives it.
 *
 * @param amount a decimal string, never a JSON number - the same reason salaries leave as strings
 * @param reason mandatory: an unexplained change is not an audit record
 * @param actorId who is making the change. Temporary shape: it belongs in the authenticated
 *     principal and moves there at 2.9. Until then it is validated against the user table rather
 *     than trusted, and there is no default - a revision credited to an invented system account
 *     would answer "who changed this" with a fiction.
 */
record ChangeSalaryRequest(String amount, String currency, String reason, String note, UUID actorId) {

    ChangeSalaryCommand toCommand(UUID employeeId) {
        if (amount == null || currency == null || reason == null || actorId == null) {
            throw new IllegalArgumentException("amount, currency, reason and actorId are all required");
        }
        return new ChangeSalaryCommand(
                new EmployeeId(employeeId),
                Money.of(new BigDecimal(amount), new CurrencyCode(currency)),
                ChangeReason.valueOf(reason.toUpperCase(java.util.Locale.ROOT)),
                new UserId(actorId),
                note);
    }
}
