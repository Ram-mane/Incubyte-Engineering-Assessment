package com.acme.salarymanagement.employee.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class EmailAddressTest {

    @Test
    void an_email_address_carries_its_value() {
        assertThat(new EmailAddress("alice@acme.test").value()).isEqualTo("alice@acme.test");
    }

    @Test
    void case_does_not_make_two_different_people() {
        assertThat(new EmailAddress("Alice@ACME.test")).isEqualTo(new EmailAddress("alice@acme.test"));
    }

    @Test
    void something_without_an_at_sign_is_not_an_address() {
        assertThatThrownBy(() -> new EmailAddress("alice.acme.test")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void an_address_with_no_local_part_is_rejected() {
        assertThatThrownBy(() -> new EmailAddress("@acme.test")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void an_address_with_no_domain_is_rejected() {
        assertThatThrownBy(() -> new EmailAddress("alice@")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void an_address_with_more_than_one_at_sign_is_rejected() {
        assertThatThrownBy(() -> new EmailAddress("alice@acme@test")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void an_address_without_a_value_is_rejected() {
        assertThatThrownBy(() -> new EmailAddress(null)).isInstanceOf(NullPointerException.class);
    }
}
