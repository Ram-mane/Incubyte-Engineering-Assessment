package com.acme.salarymanagement.employee.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * A typed wrapper so that findBy(employeeNumber, country) cannot be called with its arguments
 * swapped - the compiler rejects it rather than the query returning nothing.
 */
class EmployeeNumberTest {

    @Test
    void an_employee_number_carries_its_value() {
        assertThat(new EmployeeNumber("E00042").value()).isEqualTo("E00042");
    }

    @Test
    void surrounding_whitespace_is_trimmed_because_it_comes_from_spreadsheets() {
        assertThat(new EmployeeNumber("  E00042  ")).isEqualTo(new EmployeeNumber("E00042"));
    }

    @Test
    void a_blank_employee_number_identifies_nobody_and_is_rejected() {
        assertThatThrownBy(() -> new EmployeeNumber("   ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void an_employee_number_without_a_value_is_rejected() {
        assertThatThrownBy(() -> new EmployeeNumber(null)).isInstanceOf(NullPointerException.class);
    }
}
