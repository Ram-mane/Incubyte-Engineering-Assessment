package com.acme.salarymanagement.employee.adapter.in.web;

import java.math.BigDecimal;

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
 *     <p>Who is making the change is deliberately <em>not</em> here. It comes from the
 *     authenticated principal, so a caller cannot credit a pay change to somebody else by editing
 *     a field - which is what an audit log exists to make impossible.
 */
record ChangeSalaryRequest(String amount, String currency, String reason, String note) {

    ChangeSalaryCommand toCommand(java.util.UUID employeeId, UserId actor) {
        if (amount == null || currency == null || reason == null) {
            throw new IllegalArgumentException("amount, currency and reason are all required");
        }
        return new ChangeSalaryCommand(
                new EmployeeId(employeeId),
                Money.of(new BigDecimal(amount), new CurrencyCode(currency)),
                ChangeReason.valueOf(reason.toUpperCase(java.util.Locale.ROOT)),
                actor,
                note);
    }
}
