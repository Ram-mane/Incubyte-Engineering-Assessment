package com.acme.salarymanagement.analytics.application.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acme.salarymanagement.analytics.application.port.in.DashboardFilters;
import com.acme.salarymanagement.analytics.application.port.in.GetPayrollSummary;
import com.acme.salarymanagement.analytics.application.port.in.PayrollSummary;
import com.acme.salarymanagement.analytics.application.port.out.PayrollSummaryRepository;

@Service
class GetPayrollSummaryService implements GetPayrollSummary {

    private final PayrollSummaryRepository summaries;

    GetPayrollSummaryService(PayrollSummaryRepository summaries) {
        this.summaries = summaries;
    }

    @Override
    // An analyst reads the dashboard. That is the whole point of the role.
    @PreAuthorize("hasAnyRole('HR_MANAGER','HR_ANALYST')")
    @Transactional(readOnly = true)
    public PayrollSummary of(DashboardFilters filters) {
        return summaries.summarise(filters == null ? DashboardFilters.everyone() : filters);
    }
}
