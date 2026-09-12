package com.acme.salarymanagement.analytics.domain;

import java.time.LocalDate;
import java.util.List;

/**
 * Some salary in the result could not be converted to the reporting currency.
 *
 * <p>This exists because the alternative is worse. Converting an unrateable salary at 1.0 turned
 * 1,000,000 INR into 1,000,000 EUR and reported India as the organisation's largest payroll cost -
 * a plausible-looking number, in the reporting currency, with no error anywhere. A dashboard that
 * refuses is recoverable; a dashboard that lies is not. See ADR-0015.
 *
 * <p>Names currencies and a date, never an amount (D080), so it is safe to return to a client.
 */
public class UnconvertibleSalaries extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public UnconvertibleSalaries(String reportingCurrency, LocalDate asOf, List<String> currencies) {
        super(message(reportingCurrency, asOf, currencies));
    }

    /**
     * The currencies are the actionable half of this message, so they are named whether or not
     * there is a date to name - a table holding no rates to the reporting currency at all is
     * exactly when an operator most needs to be told which currencies would have needed one.
     */
    private static String message(String reportingCurrency, LocalDate asOf, List<String> currencies) {
        String missing = String.join(", ", currencies.stream().sorted().toList());
        if (asOf == null) {
            return ("No exchange rates to %s at all, and %s would need one. "
                            + "Seed rates to that currency, or report in one the table covers.")
                    .formatted(reportingCurrency, missing);
        }
        return "No exchange rate to %s as of %s for %s. The rate table must cover every currency in the result."
                .formatted(reportingCurrency, asOf, missing);
    }
}
