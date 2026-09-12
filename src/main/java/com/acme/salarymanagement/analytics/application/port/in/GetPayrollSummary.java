package com.acme.salarymanagement.analytics.application.port.in;

/** The dashboard's headline question: what does this organisation pay, and to how many people. */
public interface GetPayrollSummary {

    PayrollSummary of(DashboardFilters filters);
}
