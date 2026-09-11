package com.acme.salarymanagement.employee.domain;

import java.util.UUID;

public record EmployeeId(UUID value) {

    public static EmployeeId newId() {
        return new EmployeeId(UUID.randomUUID());
    }
}
