package com.acme.salarymanagement.analytics.application.port.in;

/** How pay is spread within a peer group: quartiles and range per role or department. */
public interface GetSalaryDistribution {

    SalaryDistribution by(DistributionDimension dimension, DashboardFilters filters);
}
