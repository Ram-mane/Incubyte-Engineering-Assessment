package com.acme.salarymanagement.analytics.adapter.in.web;

import java.time.LocalDate;

import com.acme.salarymanagement.analytics.application.port.in.PayrollSummary;
import com.acme.salarymanagement.shared.Money;

/** The four cards as JSON. Money is a string amount with its currency, as everywhere else. */
record PayrollSummaryResponse(
        long headcount,
        Amount totalSpend,
        Amount averageSalary,
        Amount medianSalary,
        String reportingCurrency,
        LocalDate ratesAsOf) {

    static PayrollSummaryResponse of(PayrollSummary summary) {
        return new PayrollSummaryResponse(
                summary.headcount(),
                Amount.of(summary.totalSpend()),
                Amount.of(summary.averageSalary()),
                Amount.of(summary.medianSalary()),
                summary.totalSpend().currency().code(),
                summary.ratesAsOf());
    }

    record Amount(String amount, String currency) {

        static Amount of(Money money) {
            return new Amount(money.amount().toPlainString(), money.currency().code());
        }
    }
}
