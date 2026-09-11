package com.acme.salarymanagement.band.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.acme.salarymanagement.shared.CountryCode;
import com.acme.salarymanagement.shared.CurrencyCode;
import com.acme.salarymanagement.shared.CurrencyMismatchException;
import com.acme.salarymanagement.shared.JobTitle;
import com.acme.salarymanagement.shared.Money;
import com.acme.salarymanagement.shared.SeniorityLevel;

/**
 * The approved pay range for one role in one market, and where a salary sits inside it.
 *
 * <p>A band is <em>displayed, never enforced</em>. Nothing here blocks a salary outside the range
 * or raises an alert: the system reports where someone sits and leaves the judgement to the HR
 * Manager. That was confirmed with the customer, and it is why every method below returns a
 * classification rather than throwing.
 */
class SalaryBandTest {

    private static final CurrencyCode INR = new CurrencyCode("INR");
    private static final CurrencyCode EUR = new CurrencyCode("EUR");
    private static final CountryCode INDIA = new CountryCode("IN");
    private static final JobTitle ENGINEER = new JobTitle("Software Engineer");

    private static Money rupees(String amount) {
        return Money.of(amount, INR);
    }

    /** min 1,000,000 · mid 1,250,000 · max 1,500,000 — so 90% of mid is 1,125,000. */
    private static SalaryBand seniorEngineerInIndia() {
        return new SalaryBand(
                ENGINEER,
                SeniorityLevel.SENIOR,
                INDIA,
                rupees("1000000.00"),
                rupees("1250000.00"),
                rupees("1500000.00"));
    }

    @Nested
    class Bounds {

        @Test
        void a_band_runs_from_its_minimum_through_its_midpoint_to_its_maximum() {
            var band = seniorEngineerInIndia();

            assertThat(band.min()).isEqualTo(rupees("1000000.00"));
            assertThat(band.mid()).isEqualTo(rupees("1250000.00"));
            assertThat(band.max()).isEqualTo(rupees("1500000.00"));
        }

        @Test
        void a_band_carries_the_natural_key_it_is_found_by() {
            // It has no id of its own (D083), so role, level and country are how a band is
            // located - the unique key in 05-DATA-MODEL.md and the only way to match an employee.
            var band = seniorEngineerInIndia();

            assertThat(band.jobTitle()).isEqualTo(ENGINEER);
            assertThat(band.level()).isEqualTo(SeniorityLevel.SENIOR);
            assertThat(band.country()).isEqualTo(INDIA);
        }

        @Test
        void a_midpoint_below_the_minimum_is_rejected() {
            // I10.
            assertThatThrownBy(() -> new SalaryBand(
                            ENGINEER,
                            SeniorityLevel.SENIOR,
                            INDIA,
                            rupees("1000000.00"),
                            rupees("900000.00"),
                            rupees("1500000.00")))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void a_maximum_below_the_midpoint_is_rejected() {
            assertThatThrownBy(() -> new SalaryBand(
                            ENGINEER,
                            SeniorityLevel.SENIOR,
                            INDIA,
                            rupees("1000000.00"),
                            rupees("1250000.00"),
                            rupees("1100000.00")))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void a_band_whose_bounds_are_all_equal_is_a_single_approved_rate_not_an_error() {
            var fixedRate = new SalaryBand(
                    ENGINEER,
                    SeniorityLevel.JUNIOR,
                    INDIA,
                    rupees("800000.00"),
                    rupees("800000.00"),
                    rupees("800000.00"));

            assertThat(fixedRate.positionOf(rupees("800000.00")).position()).isEqualTo(Position.WITHIN);
        }

        @Test
        void a_band_mixing_currencies_is_rejected() {
            // I10. A range whose bounds are in different currencies describes no range at all.
            assertThatThrownBy(() -> new SalaryBand(
                            ENGINEER,
                            SeniorityLevel.SENIOR,
                            INDIA,
                            rupees("1000000.00"),
                            Money.of("14000.00", EUR),
                            rupees("1500000.00")))
                    .isInstanceOf(CurrencyMismatchException.class);
        }

        // One test per bound. The original set all three to zero at once, so narrowing the guard
        // to the midpoint alone - the only bound the ratio divides by - passed all twenty tests.

        @Test
        void a_band_with_a_zero_minimum_is_rejected() {
            assertThatThrownBy(() -> new SalaryBand(
                            ENGINEER,
                            SeniorityLevel.SENIOR,
                            INDIA,
                            rupees("0.00"),
                            rupees("1250000.00"),
                            rupees("1500000.00")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("greater than zero");
        }

        @Test
        void a_band_with_a_zero_midpoint_is_rejected() {
            assertThatThrownBy(() -> new SalaryBand(
                            ENGINEER,
                            SeniorityLevel.SENIOR,
                            INDIA,
                            rupees("1000000.00"),
                            rupees("0.00"),
                            rupees("1500000.00")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("greater than zero");
        }

        @Test
        void a_band_with_a_zero_maximum_is_rejected() {
            assertThatThrownBy(() -> new SalaryBand(
                            ENGINEER,
                            SeniorityLevel.SENIOR,
                            INDIA,
                            rupees("1000000.00"),
                            rupees("1250000.00"),
                            rupees("0.00")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("greater than zero");
        }
    }

    @Nested
    class CompaRatio {

        @Test
        void a_salary_at_the_midpoint_has_a_compa_ratio_of_one() {
            var position = seniorEngineerInIndia().positionOf(rupees("1250000.00"));

            assertThat(position.compaRatio()).isEqualByComparingTo(BigDecimal.ONE);
        }

        @Test
        void the_compa_ratio_is_the_salary_divided_by_the_midpoint() {
            var position = seniorEngineerInIndia().positionOf(rupees("1000000.00"));

            assertThat(position.compaRatio()).isEqualByComparingTo(new BigDecimal("0.8000"));
        }

        @Test
        void the_compa_ratio_is_held_to_four_decimal_places() {
            // 1,300,000 / 1,250,000 = 1.04 exactly; 1,234,567 / 1,250,000 recurs and must not.
            var position = seniorEngineerInIndia().positionOf(rupees("1234567.00"));

            assertThat(position.compaRatio().scale()).isEqualTo(4);
            assertThat(position.compaRatio()).isEqualByComparingTo(new BigDecimal("0.9877"));
        }

        @Test
        void a_salary_in_another_currency_cannot_be_placed_in_the_band() {
            assertThatThrownBy(() -> seniorEngineerInIndia().positionOf(Money.of("45000.00", EUR)))
                    .isInstanceOf(CurrencyMismatchException.class);
        }
    }

    @Nested
    class Classification {

        @Test
        void a_salary_below_the_minimum_is_below_the_band() {
            assertThat(seniorEngineerInIndia().positionOf(rupees("999999.99")).position())
                    .isEqualTo(Position.BELOW_MIN);
        }

        @Test
        void a_salary_above_the_maximum_is_above_the_band() {
            assertThat(seniorEngineerInIndia().positionOf(rupees("1500000.01")).position())
                    .isEqualTo(Position.ABOVE_MAX);
        }

        @Test
        void a_salary_exactly_at_the_minimum_is_inside_the_band() {
            assertThat(seniorEngineerInIndia().positionOf(rupees("1000000.00")).position())
                    .isEqualTo(Position.LOW);
        }

        @Test
        void a_salary_at_ninety_percent_of_the_midpoint_is_within_the_band() {
            // WITHIN is 0.9 to 1.1 inclusive, so the boundary itself is not LOW.
            assertThat(seniorEngineerInIndia().positionOf(rupees("1125000.00")).position())
                    .isEqualTo(Position.WITHIN);
        }

        @Test
        void a_salary_just_under_ninety_percent_of_the_midpoint_is_low() {
            assertThat(seniorEngineerInIndia().positionOf(rupees("1124000.00")).position())
                    .isEqualTo(Position.LOW);
        }

        @Test
        void a_salary_at_one_hundred_and_ten_percent_of_the_midpoint_is_within_the_band() {
            assertThat(seniorEngineerInIndia().positionOf(rupees("1375000.00")).position())
                    .isEqualTo(Position.WITHIN);
        }

        @Test
        void a_salary_just_over_one_hundred_and_ten_percent_of_the_midpoint_is_high() {
            assertThat(seniorEngineerInIndia().positionOf(rupees("1376000.00")).position())
                    .isEqualTo(Position.HIGH);
        }

        @Test
        void being_outside_the_band_is_reported_and_never_blocked() {
            // The whole point: an out-of-band salary produces a classification, not an exception.
            var wayOver = seniorEngineerInIndia().positionOf(rupees("3000000.00"));

            assertThat(wayOver.position()).isEqualTo(Position.ABOVE_MAX);
            assertThat(wayOver.compaRatio()).isEqualByComparingTo(new BigDecimal("2.4000"));
        }
    }

    @Nested
    class NoBand {

        @Test
        void an_employee_whose_role_has_no_band_has_no_position_at_all() {
            // Undefined is modelled as absent, never as 0 or -1: a compa-ratio of zero would be
            // read as "paid nothing", and -1 is not a ratio.
            Optional<SalaryBand> noBandMatched = Optional.empty();

            Optional<BandPosition> position = noBandMatched.map(band -> band.positionOf(rupees("1200000.00")));

            assertThat(position).isEmpty();
        }
    }
}
