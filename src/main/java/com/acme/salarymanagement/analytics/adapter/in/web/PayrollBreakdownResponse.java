package com.acme.salarymanagement.analytics.adapter.in.web;

import java.time.LocalDate;
import java.util.List;

import com.acme.salarymanagement.analytics.application.port.in.PayrollBreakdown;
import com.acme.salarymanagement.shared.Money;

/** Spend per group as JSON. Money is a string amount with its currency, as everywhere else. */
record PayrollBreakdownResponse(String groupedBy, List<Group> groups, String reportingCurrency, LocalDate ratesAsOf) {

    static PayrollBreakdownResponse of(PayrollBreakdown breakdown, String reportingCurrency) {
        return new PayrollBreakdownResponse(
                breakdown.dimension().name().toLowerCase(java.util.Locale.ROOT),
                breakdown.rows().stream().map(Group::of).toList(),
                reportingCurrency,
                breakdown.ratesAsOf());
    }

    record Group(String name, long headcount, Amount totalSpend, Amount averageSalary) {

        static Group of(PayrollBreakdown.BreakdownRow row) {
            return new Group(row.group(), row.headcount(), Amount.of(row.totalSpend()), Amount.of(row.averageSalary()));
        }
    }

    record Amount(String amount, String currency) {

        static Amount of(Money money) {
            return new Amount(money.amount().toPlainString(), money.currency().code());
        }
    }
}
