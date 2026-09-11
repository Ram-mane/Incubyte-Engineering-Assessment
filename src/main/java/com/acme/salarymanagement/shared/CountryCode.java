package com.acme.salarymanagement.shared;

import java.util.Currency;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * An ISO-3166 country, and the currency its employees are paid in.
 *
 * <p>The currency comes from the JDK rather than a table maintained here, for the same reason
 * {@link CurrencyCode}'s scale does: a hand-written map is a second source of truth that is wrong
 * the first time the company hires somewhere nobody anticipated.
 *
 * <p>This narrows what counts as valid input. A country the JDK has no currency for - Antarctica
 * is one - is rejected at construction, so {@link #currency()} never has to return null or throw.
 */
public record CountryCode(String code) {

    private static final Set<String> ISO_COUNTRIES = Set.of(Locale.getISOCountries());

    public CountryCode {
        Objects.requireNonNull(code, "country code is required");
        if (!ISO_COUNTRIES.contains(code)) {
            throw new IllegalArgumentException("%s is not an ISO-3166 country code".formatted(code));
        }
        if (currencyOf(code) == null) {
            throw new IllegalArgumentException("%s has no currency, so nobody can be paid there".formatted(code));
        }
    }

    /** The currency employees in this country are paid in. */
    public CurrencyCode currency() {
        return new CurrencyCode(currencyOf(code).getCurrencyCode());
    }

    private static Currency currencyOf(String code) {
        return Currency.getInstance(Locale.of("", code));
    }
}
