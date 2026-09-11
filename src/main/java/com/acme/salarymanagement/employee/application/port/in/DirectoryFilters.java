package com.acme.salarymanagement.employee.application.port.in;

import com.acme.salarymanagement.shared.CountryCode;
import com.acme.salarymanagement.shared.Department;
import com.acme.salarymanagement.shared.JobTitle;
import com.acme.salarymanagement.shared.SeniorityLevel;

/**
 * The four dimensions the customer named, each optional.
 *
 * <p>Typed rather than four Strings, so two filters cannot be passed in the wrong order and an
 * impossible country is rejected at the edge instead of quietly matching nobody.
 *
 * <p>Band position is deliberately absent. Bands are displayed, never enforced, and a filter for
 * "outside band" would make the system look like it polices pay.
 */
public record DirectoryFilters(CountryCode country, Department department, JobTitle jobTitle, SeniorityLevel level) {

    public static DirectoryFilters none() {
        return new DirectoryFilters(null, null, null, null);
    }
}
