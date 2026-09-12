package com.acme.salarymanagement.analytics.application.port.in;

import com.acme.salarymanagement.shared.CountryCode;
import com.acme.salarymanagement.shared.CurrencyCode;
import com.acme.salarymanagement.shared.Department;
import com.acme.salarymanagement.shared.JobTitle;
import com.acme.salarymanagement.shared.SeniorityLevel;

/**
 * What the dashboard is looking at, and what it is reporting in.
 *
 * <p>Its own type rather than the directory's: `analytics` and `employee` are separate modules and
 * each owns its request contract. What they share is the vocabulary - `CountryCode`, `Department`,
 * `JobTitle`, `SeniorityLevel` all live in the kernel - not the shape of a query.
 *
 * @param reportingCurrency what every figure is normalised to. USD by default, because the customer
 *     said so, not because it is a currency anyone here is paid in.
 */
public record DashboardFilters(
        CountryCode country,
        Department department,
        JobTitle jobTitle,
        SeniorityLevel level,
        CurrencyCode reportingCurrency) {

    public static final CurrencyCode DEFAULT_REPORTING_CURRENCY = new CurrencyCode("USD");

    public static DashboardFilters everyone() {
        return new DashboardFilters(null, null, null, null, DEFAULT_REPORTING_CURRENCY);
    }

    public DashboardFilters withCountry(CountryCode narrowed) {
        return new DashboardFilters(narrowed, department, jobTitle, level, reportingCurrency);
    }

    public DashboardFilters withDepartment(Department narrowed) {
        return new DashboardFilters(country, narrowed, jobTitle, level, reportingCurrency);
    }
}
