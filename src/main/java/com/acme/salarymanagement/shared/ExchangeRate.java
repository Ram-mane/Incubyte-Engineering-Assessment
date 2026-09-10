package com.acme.salarymanagement.shared;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * A dated fact: one unit of {@code from} bought {@code rate} units of {@code to} on {@code asOf}.
 *
 * <p>The date is part of the value because a converted figure that cannot say which rate produced
 * it is not auditable - and a compensation report that cannot be reproduced is not evidence.
 *
 * <p>Resolving which rate applies on a given date is not here: aggregates normalise in the SQL join
 * against the seeded FX table, so a Java-side rate table would be code the dashboard never calls.
 */
public record ExchangeRate(CurrencyCode from, CurrencyCode to, BigDecimal rate, LocalDate asOf) {

    public ExchangeRate {
        Objects.requireNonNull(from, "the source currency is required");
        Objects.requireNonNull(to, "the target currency is required");
        Objects.requireNonNull(rate, "the rate is required");
        Objects.requireNonNull(asOf, "the date the rate applied on is required");
        if (rate.signum() <= 0) {
            // A zero rate does not fail loudly: it converts a payroll to nothing and reports it.
            throw new IllegalArgumentException("an exchange rate must be greater than zero, but was " + rate);
        }
    }
}
