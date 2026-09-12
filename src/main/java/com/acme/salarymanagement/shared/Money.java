package com.acme.salarymanagement.shared;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * An amount in a currency. Never a binary floating point number - see ADR-0006.
 *
 * <p>Zero and negative amounts are legitimate: an empty dashboard filter sums to zero, and an
 * employee paid under their band's minimum sits a negative distance from it. "A salary must be
 * positive" is a rule about salaries, and lives on {@code Employee} and in a database CHECK.
 *
 * <p>Amounts are normalised to the currency's scale at construction, which is also what makes
 * equality behave: {@code BigDecimal.equals} distinguishes 100.0 from 100.00, and money does not.
 */
public record Money(BigDecimal amount, CurrencyCode currency) {

    public Money {
        Objects.requireNonNull(currency, "currency is required");
        Objects.requireNonNull(amount, "amount is required");
        amount = amount.setScale(currency.scale(), RoundingMode.HALF_EVEN);
    }

    /**
     * Builds money from its decimal representation. Preferred over the {@link BigDecimal} overload
     * for literals: {@code new BigDecimal(0.1)} is not 0.1, whereas {@code "0.1"} is.
     */
    public static Money of(String amount, CurrencyCode currency) {
        return new Money(new BigDecimal(amount), currency);
    }

    public static Money of(BigDecimal amount, CurrencyCode currency) {
        return new Money(amount, currency);
    }

    public Money plus(Money other) {
        requireSameCurrencyAs(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money minus(Money other) {
        requireSameCurrencyAs(other);
        return new Money(amount.subtract(other.amount), currency);
    }

    /** Whether this is an amount someone could be paid. Invariant I1 asks only this much. */
    public boolean isPositive() {
        return amount.signum() > 0;
    }

    private void requireSameCurrencyAs(Money other) {
        if (!currency.equals(other.currency)) {
            throw new CurrencyMismatchException("cannot combine %s with %s without an explicit exchange rate"
                    .formatted(currency.code(), other.currency.code()));
        }
    }

    @Override
    public String toString() {
        return amount + " " + currency.code();
    }
}
