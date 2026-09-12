package com.acme.salarymanagement.analytics.domain;

import java.time.LocalDate;
import java.util.List;

/**
 * Some salary in the result could not be converted to the reporting currency.
 *
 * <p>This exists because the alternative is worse. Converting an unrateable salary at 1.0 turned
 * 1,000,000 INR into 1,000,000 EUR and reported India as the organisation's largest payroll cost -
 * a plausible-looking number, in the reporting currency, with no error anywhere. A dashboard that
 * refuses is recoverable; a dashboard that lies is not.
 *
 * <p>Names currencies and a date, never an amount (D080), so it is safe to return to a client.
 */
public class UnconvertibleSalaries extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public UnconvertibleSalaries(String reportingCurrency, LocalDate asOf, List<String> currencies) {
        super("No exchange rate to %s as of %s for %s. The rate table must cover every currency in the result."
                .formatted(
                        reportingCurrency,
                        asOf,
                        String.join(", ", currencies.stream().sorted().toList())));
    }

    public UnconvertibleSalaries(String reportingCurrency) {
        super("No exchange rates to %s at all. Seed rates to that currency, or report in one the table covers."
                .formatted(reportingCurrency));
    }
}
