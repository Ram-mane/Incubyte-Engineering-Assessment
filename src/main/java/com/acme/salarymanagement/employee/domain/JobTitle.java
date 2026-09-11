package com.acme.salarymanagement.employee.domain;

import java.util.Objects;

public record JobTitle(String value) {

    public JobTitle {
        Objects.requireNonNull(value, "a job title is required");
        value = value.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("a job title is required");
        }
    }
}
