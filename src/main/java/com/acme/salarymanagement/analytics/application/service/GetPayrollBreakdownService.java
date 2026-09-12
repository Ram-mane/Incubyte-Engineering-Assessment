package com.acme.salarymanagement.analytics.application.service;

import java.util.Objects;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acme.salarymanagement.analytics.application.port.in.BreakdownDimension;
import com.acme.salarymanagement.analytics.application.port.in.DashboardFilters;
import com.acme.salarymanagement.analytics.application.port.in.GetPayrollBreakdown;
import com.acme.salarymanagement.analytics.application.port.in.PayrollBreakdown;
import com.acme.salarymanagement.analytics.application.port.out.PayrollBreakdownRepository;

@Service
class GetPayrollBreakdownService implements GetPayrollBreakdown {

    private final PayrollBreakdownRepository breakdowns;

    GetPayrollBreakdownService(PayrollBreakdownRepository breakdowns) {
        this.breakdowns = breakdowns;
    }

    @Override
    // An analyst reads the dashboard. That is the whole point of the role.
    @PreAuthorize("hasAnyRole('HR_MANAGER','HR_ANALYST')")
    @Transactional(readOnly = true)
    public PayrollBreakdown by(BreakdownDimension dimension, DashboardFilters filters) {
        Objects.requireNonNull(dimension, "a breakdown must say what it is a breakdown of");
        return breakdowns.breakDown(dimension, filters == null ? DashboardFilters.everyone() : filters);
    }
}
