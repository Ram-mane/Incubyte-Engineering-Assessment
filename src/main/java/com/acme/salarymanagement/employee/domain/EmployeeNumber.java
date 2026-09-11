package com.acme.salarymanagement.employee.domain;

import java.util.Objects;

/**
 * The company's own identifier for a person, distinct from the system's {@link EmployeeId}. Typed
 * so that a lookup taking a number and a country cannot be called with its arguments swapped.
 */
public record EmployeeNumber(String value) {

    public EmployeeNumber {
        Objects.requireNonNull(value, "an employee number is required");
        // These arrive from spreadsheets, where trailing spaces are invisible and free.
        value = value.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("an employee number identifies someone; blank identifies nobody");
        }
    }
}
