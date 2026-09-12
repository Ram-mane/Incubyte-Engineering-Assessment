package com.acme.salarymanagement.analytics.adapter.in.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.acme.salarymanagement.analytics.application.port.in.BreakdownDimension;
import com.acme.salarymanagement.analytics.application.port.in.DashboardFilters;
import com.acme.salarymanagement.analytics.application.port.in.DistributionDimension;
import com.acme.salarymanagement.analytics.application.port.in.GetPayrollBreakdown;
import com.acme.salarymanagement.analytics.application.port.in.GetPayrollSummary;
import com.acme.salarymanagement.analytics.application.port.in.GetSalaryDistribution;
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
    private final GetPayrollBreakdown breakdowns;
    private final GetSalaryDistribution distributions;

    DashboardController(
            GetPayrollSummary summaries, GetPayrollBreakdown breakdowns, GetSalaryDistribution distributions) {
        this.summaries = summaries;
        this.breakdowns = breakdowns;
        this.distributions = distributions;
    }

    @GetMapping("/summary")
    PayrollSummaryResponse summary(
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String jobTitle,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String currency) {

        return PayrollSummaryResponse.of(summaries.of(filtersFrom(country, department, jobTitle, level, currency)));
    }

    /**
     * Where the money goes. {@code groupBy} is bound to an enum rather than passed through: the
     * grouping column cannot be a query parameter, so the set of things it can be is closed here
     * and an unknown value is a 400 rather than anything reaching SQL.
     */
    @GetMapping("/breakdown")
    PayrollBreakdownResponse breakdown(
            @RequestParam BreakdownDimension groupBy,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String jobTitle,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String currency) {

        DashboardFilters filters = filtersFrom(country, department, jobTitle, level, currency);
        return PayrollBreakdownResponse.of(
                breakdowns.by(groupBy, filters), filters.reportingCurrency().code());
    }

    /** How pay is spread inside a peer group: quartiles and range per role or department. */
    @GetMapping("/distribution")
    SalaryDistributionResponse distribution(
            @RequestParam DistributionDimension groupBy,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String jobTitle,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String currency) {

        DashboardFilters filters = filtersFrom(country, department, jobTitle, level, currency);
        return SalaryDistributionResponse.of(
                distributions.by(groupBy, filters), filters.reportingCurrency().code());
    }

    private static DashboardFilters filtersFrom(
            String country, String department, String jobTitle, String level, String currency) {

        return new DashboardFilters(
                country == null ? null : new CountryCode(country),
                department == null ? null : new Department(department),
                jobTitle == null ? null : new JobTitle(jobTitle),
                level == null ? null : SeniorityLevel.valueOf(level.toUpperCase(java.util.Locale.ROOT)),
                currency == null
                        ? DashboardFilters.DEFAULT_REPORTING_CURRENCY
                        : new CurrencyCode(currency.toUpperCase(java.util.Locale.ROOT)));
    }
}
