package com.acme.salarymanagement.analytics.application.service;

import java.util.Objects;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acme.salarymanagement.analytics.application.port.in.DashboardFilters;
import com.acme.salarymanagement.analytics.application.port.in.DistributionDimension;
import com.acme.salarymanagement.analytics.application.port.in.GetSalaryDistribution;
import com.acme.salarymanagement.analytics.application.port.in.SalaryDistribution;
import com.acme.salarymanagement.analytics.application.port.out.SalaryDistributionRepository;

@Service
class GetSalaryDistributionService implements GetSalaryDistribution {

    private final SalaryDistributionRepository distributions;

    GetSalaryDistributionService(SalaryDistributionRepository distributions) {
        this.distributions = distributions;
    }

    @Override
    // An analyst reads the dashboard. That is the whole point of the role.
    @PreAuthorize("hasAnyRole('HR_MANAGER','HR_ANALYST')")
    @Transactional(readOnly = true)
    public SalaryDistribution by(DistributionDimension dimension, DashboardFilters filters) {
        Objects.requireNonNull(dimension, "a distribution must say what it is a distribution of");
        return distributions.distribute(dimension, filters == null ? DashboardFilters.everyone() : filters);
    }
}
