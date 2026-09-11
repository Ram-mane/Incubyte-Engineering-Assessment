package com.acme.salarymanagement.employee.application.port.out;

import com.acme.salarymanagement.employee.domain.UserId;

/** Whether the user credited with a change is a user this system knows. */
public interface ActingUsers {

    boolean exists(UserId actor);
}
