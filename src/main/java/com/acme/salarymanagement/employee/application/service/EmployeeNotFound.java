package com.acme.salarymanagement.employee.application.service;

import com.acme.salarymanagement.employee.domain.EmployeeId;

/** Asked to change the pay of somebody who is not here. The caller's mistake, not the domain's. */
public class EmployeeNotFound extends RuntimeException {

    private static final long serialVersionUID = 1L;

    // Public because it crosses the port boundary: the web adapter maps it to a 404, and a
    // failure mode adapters must handle is part of what the application layer publishes.
    public EmployeeNotFound(EmployeeId id) {
        super("no employee with id %s".formatted(id.value()));
    }
}
