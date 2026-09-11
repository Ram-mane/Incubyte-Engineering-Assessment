package com.acme.salarymanagement.employee.application.port.out;

import java.util.UUID;

import com.acme.salarymanagement.shared.CountryCode;
import com.acme.salarymanagement.shared.Department;
import com.acme.salarymanagement.shared.JobTitle;
import com.acme.salarymanagement.shared.Money;
import com.acme.salarymanagement.shared.SeniorityLevel;

/**
 * One row of the directory, as the screen needs it.
 *
 * <p>A projection rather than an {@code Employee}: the directory shows ten thousand people fifty
 * at a time and does nothing to any of them, so loading aggregates would be paying for behaviour
 * nobody calls. The read model and the write model are allowed to differ.
 */
public record EmployeeSummary(
        UUID id,
        String employeeNumber,
        String givenName,
        String familyName,
        String email,
        CountryCode country,
        Department department,
        JobTitle jobTitle,
        SeniorityLevel level,
        Money salary) {}
