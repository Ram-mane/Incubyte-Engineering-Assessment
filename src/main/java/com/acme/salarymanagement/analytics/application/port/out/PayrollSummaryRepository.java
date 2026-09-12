package com.acme.salarymanagement.analytics.application.port.out;

import com.acme.salarymanagement.analytics.application.port.in.DashboardFilters;
import com.acme.salarymanagement.analytics.application.port.in.PayrollSummary;

/**
 * Read-only, and named so. `analytics` is forbidden by an ArchUnit rule to depend on anything whose
 * name ends in {@code WriteRepository}: the dashboard reports on pay and must have no way to change
 * it.
 */
public interface PayrollSummaryRepository {

    PayrollSummary summarise(DashboardFilters filters);
}
