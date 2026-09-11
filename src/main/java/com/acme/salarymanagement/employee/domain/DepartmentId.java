package com.acme.salarymanagement.employee.domain;

import java.util.Objects;

/** A reference to the department aggregate, held by identity rather than by value. */
public record DepartmentId(Long value) {

    public DepartmentId {
        Objects.requireNonNull(value, "a department id is required");
    }
}
