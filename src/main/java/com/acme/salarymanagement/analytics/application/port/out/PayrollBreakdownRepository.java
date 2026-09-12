package com.acme.salarymanagement.analytics.application.port.out;

import com.acme.salarymanagement.analytics.application.port.in.BreakdownDimension;
import com.acme.salarymanagement.analytics.application.port.in.DashboardFilters;
import com.acme.salarymanagement.analytics.application.port.in.PayrollBreakdown;

/** Read-only, and named so — see {@link PayrollSummaryRepository}. */
public interface PayrollBreakdownRepository {

    PayrollBreakdown breakDown(BreakdownDimension dimension, DashboardFilters filters);
}
