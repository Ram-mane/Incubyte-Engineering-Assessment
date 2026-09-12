package com.acme.salarymanagement.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Money is where a whole class of payroll bugs is made impossible rather than merely discouraged.
 *
 * <p>Note what is deliberately absent: Money permits zero and negative amounts, so subtraction is
 * total. "A salary must be positive" is a rule about salaries, not about money - an empty dashboard
 * filter sums to zero, and an employee paid below their band's minimum has a negative distance from
 * it. Both are legitimate Money. That rule lives in Employee.changeSalaryTo and a database CHECK.
 *
 * <p>Conversion is absent too: it needs a dated ExchangeRate, and lands whole in its own commit.
 */
class MoneyTest {

    private static final CurrencyCode INR = new CurrencyCode("INR");
    private static final CurrencyCode USD = new CurrencyCode("USD");
    private static final CurrencyCode EUR = new CurrencyCode("EUR");
    private static final CurrencyCode JPY = new CurrencyCode("JPY");

    @Nested
    class Arithmetic {

        @Test
        void adding_two_amounts_of_the_same_currency_sums_them() {
            Money total = Money.of("1200000.00", INR).plus(Money.of("180000.00", INR));

            assertThat(total).isEqualTo(Money.of("1380000.00", INR));
        }

        @Test
        void subtracting_an_amount_of_the_same_currency_reduces_it() {
            Money remaining = Money.of("1380000.00", INR).minus(Money.of("180000.00", INR));

            assertThat(remaining).isEqualTo(Money.of("1200000.00", INR));
        }

        @Test
        void subtracting_an_equal_amount_yields_zero() {
            Money salary = Money.of("1200000.00", INR);

            assertThat(salary.minus(salary)).isEqualTo(Money.of("0.00", INR));
        }

        @Test
        void subtracting_a_larger_amount_yields_a_negative_amount() {
            Money belowMinimum = Money.of("1200000.00", INR).minus(Money.of("1500000.00", INR));

            assertThat(belowMinimum).isEqualTo(Money.of("-300000.00", INR));
        }

        @Test
        void adding_two_different_currencies_is_rejected() {
            Money rupees = Money.of("1200000.00", INR);
            Money euros = Money.of("45000.00", EUR);

            assertThatThrownBy(() -> rupees.plus(euros))
                    .isInstanceOf(CurrencyMismatchException.class)
                    .hasMessageContaining("INR")
                    .hasMessageContaining("EUR");
        }

        @Test
        void amounts_whose_currencies_are_equal_but_distinct_objects_can_be_added() {
            // Guards the difference between equals and ==. Every other test reuses the constants
            // above, so identity comparison would pass them all - while failing in production the
            // moment a JPA converter or CSV import builds a CurrencyCode per row.
            Money fromOneSource = Money.of("100.00", new CurrencyCode("INR"));
            Money fromAnother = Money.of("50.00", new CurrencyCode("INR"));

            assertThat(fromOneSource.plus(fromAnother)).isEqualTo(Money.of("150.00", new CurrencyCode("INR")));
        }

        @Test
        void subtracting_two_different_currencies_is_rejected() {
            Money rupees = Money.of("1200000.00", INR);
            Money dollars = Money.of("1000.00", USD);

            assertThatThrownBy(() -> rupees.minus(dollars)).isInstanceOf(CurrencyMismatchException.class);
        }
    }

    @Nested
    class Construction {

        @Test
        void zero_is_a_legitimate_amount_of_money() {
            assertThat(Money.of("0.00", USD).amount()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        void a_negative_amount_is_a_legitimate_distance_below_a_band_minimum() {
            assertThat(Money.of("-300000.00", INR).amount()).isEqualByComparingTo(new BigDecimal("-300000.00"));
        }

        @Test
        void money_can_be_built_from_a_big_decimal_as_well_as_a_string() {
            assertThat(Money.of(new BigDecimal("1200000.00"), INR)).isEqualTo(Money.of("1200000.00", INR));
        }

        @Test
        void money_without_a_currency_is_meaningless_and_is_rejected() {
            assertThatThrownBy(() -> Money.of("1200000.00", null)).isInstanceOf(NullPointerException.class);
        }

        @Test
        void money_without_an_amount_is_meaningless_and_is_rejected() {
            assertThatThrownBy(() -> Money.of((BigDecimal) null, INR)).isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class Scale {

        @Test
        void rupees_are_held_to_two_decimal_places() {
            assertThat(Money.of("1200000", INR).amount().scale()).isEqualTo(2);
        }

        @Test
        void yen_are_held_to_whole_units_because_the_currency_has_no_minor_unit() {
            assertThat(Money.of("5400000", JPY).amount().scale()).isZero();
        }

        @Test
        void an_amount_with_more_precision_than_the_currency_allows_is_normalised_not_rejected() {
            assertThat(Money.of("1234.567", USD)).isEqualTo(Money.of("1234.57", USD));
        }

        @Test
        void a_yen_amount_with_decimals_is_normalised_to_a_whole_unit() {
            assertThat(Money.of("5400000.4", JPY)).isEqualTo(Money.of("5400000", JPY));
        }
    }

    @Nested
    class Rounding {

        // HALF_EVEN, not HALF_UP: across ten thousand salary rows, always rounding halves upward
        // biases every payroll total upward. Banker's rounding does not.

        @Test
        void a_half_cent_rounds_down_when_the_preceding_digit_is_even() {
            assertThat(Money.of("2.005", USD).amount()).isEqualByComparingTo(new BigDecimal("2.00"));
        }

        @Test
        void a_half_cent_rounds_up_when_the_preceding_digit_is_odd() {
            assertThat(Money.of("2.015", USD).amount()).isEqualByComparingTo(new BigDecimal("2.02"));
        }

        @Test
        void a_half_yen_rounds_to_the_even_whole_unit() {
            assertThat(Money.of("2.5", JPY).amount()).isEqualByComparingTo(new BigDecimal("2"));
            assertThat(Money.of("3.5", JPY).amount()).isEqualByComparingTo(new BigDecimal("4"));
        }

        @Test
        void a_negative_half_cent_rounds_to_the_even_neighbour_too() {
            assertThat(Money.of("-2.005", USD).amount()).isEqualByComparingTo(new BigDecimal("-2.00"));
        }
    }

    @Nested
    class Currencies {

        @Test
        void a_currency_outside_iso_4217_is_rejected() {
            assertThatThrownBy(() -> new CurrencyCode("ZZZ")).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void a_currency_with_no_minor_unit_defined_is_rejected_because_its_scale_is_unknowable() {
            // XAU is gold, XDR is an IMF drawing right, XXX means "no currency". The JDK reports
            // -1 fraction digits for each, so there is no scale to normalise to.
            assertThatThrownBy(() -> new CurrencyCode("XAU")).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new CurrencyCode("XXX")).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class Sign {

        // The narrowest comparison invariant I1 needs, and no more (D034). Tested here rather than
        // only through Employee, because it is a public method on a shared value object and the
        // next caller inherits whatever contract these tests pin down.

        @Test
        void an_amount_above_zero_is_positive() {
            assertThat(Money.of("0.01", USD).isPositive()).isTrue();
        }

        @Test
        void zero_is_not_positive() {
            assertThat(Money.of("0.00", USD).isPositive()).isFalse();
        }

        @Test
        void a_negative_amount_is_not_positive() {
            assertThat(Money.of("-0.01", USD).isPositive()).isFalse();
        }

        @Test
        void an_amount_that_rounds_to_zero_is_not_positive() {
            // 0.004 USD normalises to 0.00 at construction, so it is not a payable amount even
            // though the number handed in was above zero.
            assertThat(Money.of("0.004", USD).isPositive()).isFalse();
        }
    }

    @Nested
    class Equality {

        @Test
        void the_same_amount_written_with_different_trailing_zeros_is_the_same_money() {
            assertThat(Money.of("100.00", INR)).isEqualTo(Money.of("100.0", INR));
            assertThat(Money.of("100.00", INR)).hasSameHashCodeAs(Money.of("100.0", INR));
        }

        @Test
        void the_same_number_in_a_different_currency_is_not_the_same_money() {
            assertThat(Money.of("100.00", INR)).isNotEqualTo(Money.of("100.00", USD));
        }

        @Test
        void money_reads_as_an_amount_and_its_currency() {
            assertThat(Money.of("1200000.00", INR)).hasToString("1200000.00 INR");
        }
    }
}
