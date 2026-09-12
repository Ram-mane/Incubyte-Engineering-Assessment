package com.acme.salarymanagement.analytics.application.port.in;

/** Where the money goes: spend and headcount per department, country or level. */
public interface GetPayrollBreakdown {

    PayrollBreakdown by(BreakdownDimension dimension, DashboardFilters filters);
}
