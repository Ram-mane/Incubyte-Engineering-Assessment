package com.acme.salarymanagement.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

/**
 * A rate is a dated fact: "one rupee bought 0.012 dollars on this day". The date is part of the
 * value because a conversion that cannot say which rate it used is not auditable.
 *
 * <p>Resolving which rate applies on a given date is deliberately not here. Aggregates are
 * normalised in SQL against the seeded FX table at query time, so a Java-side rate table would be
 * code nothing calls. The lookup arrives behind {@code ExchangeRateProvider} when persistence does.
 */
class ExchangeRateTest {

    private static final CurrencyCode INR = new CurrencyCode("INR");
    private static final CurrencyCode USD = new CurrencyCode("USD");
    private static final LocalDate TENTH_OF_SEPTEMBER = LocalDate.of(2026, 9, 10);

    @Test
    void a_rate_records_the_day_it_applied_on() {
        ExchangeRate rate = new ExchangeRate(INR, USD, new BigDecimal("0.012"), TENTH_OF_SEPTEMBER);

        assertThat(rate.asOf()).isEqualTo(TENTH_OF_SEPTEMBER);
    }

    @Test
    void a_rate_of_zero_is_rejected_because_it_would_silently_empty_a_payroll_total() {
        assertThatThrownBy(() -> new ExchangeRate(INR, USD, BigDecimal.ZERO, TENTH_OF_SEPTEMBER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("greater than zero");
    }

    @Test
    void a_negative_rate_is_rejected() {
        assertThatThrownBy(() -> new ExchangeRate(INR, USD, new BigDecimal("-0.012"), TENTH_OF_SEPTEMBER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void a_rate_without_a_date_is_not_auditable_and_is_rejected() {
        assertThatThrownBy(() -> new ExchangeRate(INR, USD, new BigDecimal("0.012"), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void a_rate_without_both_currencies_is_rejected() {
        assertThatThrownBy(() -> new ExchangeRate(null, USD, new BigDecimal("0.012"), TENTH_OF_SEPTEMBER))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ExchangeRate(INR, null, new BigDecimal("0.012"), TENTH_OF_SEPTEMBER))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void a_rate_without_a_number_is_rejected() {
        assertThatThrownBy(() -> new ExchangeRate(INR, USD, null, TENTH_OF_SEPTEMBER))
                .isInstanceOf(NullPointerException.class);
    }
}
