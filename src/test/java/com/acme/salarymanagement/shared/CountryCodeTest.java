package com.acme.salarymanagement.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * A country knows the currency its people are paid in. That mapping is what makes invariant I9
 * enforceable without a lookup table someone has to remember to seed.
 */
class CountryCodeTest {

    @Test
    void a_country_knows_the_currency_its_employees_are_paid_in() {
        assertThat(new CountryCode("IN").currency()).isEqualTo(new CurrencyCode("INR"));
        assertThat(new CountryCode("DE").currency()).isEqualTo(new CurrencyCode("EUR"));
        assertThat(new CountryCode("JP").currency()).isEqualTo(new CurrencyCode("JPY"));
    }

    @Test
    void every_country_this_system_operates_in_resolves_to_a_currency() {
        // docs/05-DATA-MODEL.md: the seeded dataset spans these six.
        for (String country : new String[] {"IN", "US", "DE", "GB", "SG", "AU"}) {
            assertThat(new CountryCode(country).currency()).isNotNull();
        }
    }

    @Test
    void a_code_outside_iso_3166_is_rejected() {
        assertThatThrownBy(() -> new CountryCode("ZZ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void a_territory_with_no_currency_is_rejected_at_construction_rather_than_failing_later() {
        // Antarctica is a valid ISO country with no currency. Rejecting it here means currency()
        // never has to return null or throw.
        assertThatThrownBy(() -> new CountryCode("AQ"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no currency");
    }

    @Test
    void a_lowercase_code_is_rejected_rather_than_quietly_corrected() {
        assertThatThrownBy(() -> new CountryCode("in")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void a_country_without_a_code_is_rejected() {
        assertThatThrownBy(() -> new CountryCode(null)).isInstanceOf(NullPointerException.class);
    }
}
