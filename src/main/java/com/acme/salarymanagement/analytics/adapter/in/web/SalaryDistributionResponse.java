package com.acme.salarymanagement.analytics.adapter.in.web;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import com.acme.salarymanagement.analytics.application.port.in.DistributionDimension;
import com.acme.salarymanagement.analytics.application.port.in.SalaryDistribution;
import com.acme.salarymanagement.shared.Money;

/** Quartiles and range per group as JSON. Money is a string amount with its currency. */
record SalaryDistributionResponse(String groupedBy, List<Group> groups, String reportingCurrency, LocalDate ratesAsOf) {

    static SalaryDistributionResponse of(SalaryDistribution distribution, String reportingCurrency) {
        return new SalaryDistributionResponse(
                camelCase(distribution.dimension()),
                distribution.rows().stream().map(Group::of).toList(),
                reportingCurrency,
                distribution.ratesAsOf());
    }

    /** {@code JOB_TITLE} back to {@code jobTitle}: the API spells dimensions the way the query does. */
    private static String camelCase(DistributionDimension dimension) {
        String[] words = dimension.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder spelling = new StringBuilder(words[0]);
        for (int i = 1; i < words.length; i++) {
            spelling.append(Character.toUpperCase(words[i].charAt(0))).append(words[i].substring(1));
        }
        return spelling.toString();
    }

    record Group(
            String name,
            long headcount,
            Amount lowest,
            Amount p25,
            Amount median,
            Amount p75,
            Amount p90,
            Amount highest) {

        static Group of(SalaryDistribution.DistributionRow row) {
            return new Group(
                    row.group(),
                    row.headcount(),
                    Amount.of(row.lowest()),
                    Amount.of(row.p25()),
                    Amount.of(row.median()),
                    Amount.of(row.p75()),
                    Amount.of(row.p90()),
                    Amount.of(row.highest()));
        }
    }

    record Amount(String amount, String currency) {

        static Amount of(Money money) {
            return new Amount(money.amount().toPlainString(), money.currency().code());
        }
    }
}
