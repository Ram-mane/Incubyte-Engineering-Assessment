package com.acme.salarymanagement.analytics.application.port.out;

import com.acme.salarymanagement.analytics.application.port.in.DashboardFilters;
import com.acme.salarymanagement.analytics.application.port.in.DistributionDimension;
import com.acme.salarymanagement.analytics.application.port.in.SalaryDistribution;

/** Read-only, and named so — see {@link PayrollSummaryRepository}. */
public interface SalaryDistributionRepository {

    SalaryDistribution distribute(DistributionDimension dimension, DashboardFilters filters);
}
