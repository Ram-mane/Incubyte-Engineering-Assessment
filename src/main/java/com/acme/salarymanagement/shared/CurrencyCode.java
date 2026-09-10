package com.acme.salarymanagement.shared;

import java.util.Currency;
import java.util.Objects;

/**
 * An ISO-4217 currency, validated at construction so an invalid code cannot travel through the
 * system disguised as a String.
 *
 * <p>The number of decimal places comes from the JDK rather than a table maintained here: JPY has
 * none, BHD has three, and most have two. A hand-written map would be a second source of truth that
 * is wrong the first time someone is paid in a currency nobody anticipated.
 */
public record CurrencyCode(String code) {

    public CurrencyCode {
        Objects.requireNonNull(code, "currency code is required");
        Currency currency = Currency.getInstance(code);
        if (currency.getDefaultFractionDigits() < 0) {
            throw new IllegalArgumentException(
                    "%s has no minor unit, so an amount in it has no scale to round to".formatted(code));
        }
    }

    /** Decimal places an amount in this currency is held to. */
    public int scale() {
        return Currency.getInstance(code).getDefaultFractionDigits();
    }
}
