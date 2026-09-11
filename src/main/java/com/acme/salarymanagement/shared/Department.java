package com.acme.salarymanagement.shared;

import java.util.Objects;

/**
 * The department someone works in, held by name.
 *
 * <p>Not a reference to a department aggregate, because there is no department aggregate: nothing
 * in this system does anything to a department. It is one of the three filter dimensions the
 * customer named, alongside {@link JobTitle} and {@link SeniorityLevel}, and like those two it is
 * what the dashboard groups by - so the name is the key. An id would put a join in front of every
 * aggregate that reads it, to fetch a label the query already needed.
 */
public record Department(String value) {

    public Department {
        Objects.requireNonNull(value, "a department is required");
        value = value.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("a department is required");
        }
    }
}
