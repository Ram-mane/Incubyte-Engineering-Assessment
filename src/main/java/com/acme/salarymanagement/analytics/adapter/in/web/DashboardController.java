package com.acme.salarymanagement.analytics.adapter.in.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.acme.salarymanagement.analytics.application.port.in.DashboardFilters;
import com.acme.salarymanagement.analytics.application.port.in.GetPayrollSummary;
import com.acme.salarymanagement.shared.CountryCode;
import com.acme.salarymanagement.shared.CurrencyCode;
import com.acme.salarymanagement.shared.Department;
import com.acme.salarymanagement.shared.JobTitle;
import com.acme.salarymanagement.shared.SeniorityLevel;

/** The dashboard. Read-only by construction: this module has no write port to reach for. */
@RestController
@RequestMapping("/api/v1/dashboard")
class DashboardController {

    private final GetPayrollSummary summaries;

    DashboardController(GetPayrollSummary summaries) {
        this.summaries = summaries;
    }

    @GetMapping("/summary")
    PayrollSummaryResponse summary(
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String jobTitle,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String currency) {

        DashboardFilters filters = new DashboardFilters(
                country == null ? null : new CountryCode(country),
                department == null ? null : new Department(department),
                jobTitle == null ? null : new JobTitle(jobTitle),
                level == null ? null : SeniorityLevel.valueOf(level.toUpperCase(java.util.Locale.ROOT)),
                currency == null
                        ? DashboardFilters.DEFAULT_REPORTING_CURRENCY
                        : new CurrencyCode(currency.toUpperCase(java.util.Locale.ROOT)));

        return PayrollSummaryResponse.of(summaries.of(filters));
    }
}
