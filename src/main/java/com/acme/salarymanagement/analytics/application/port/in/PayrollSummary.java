package com.acme.salarymanagement.analytics.application.port.in;

import java.time.LocalDate;

import com.acme.salarymanagement.shared.Money;

/**
 * The four KPI cards, from one query.
 *
 * @param headcount how many active employees match the filters
 * @param totalSpend the sum of their salaries, normalised
 * @param averageSalary the mean
 * @param medianSalary the middle salary — an amount somebody is actually paid, see the adapter
 * @param ratesAsOf the date whose exchange rates every figure here was converted through. Reported
 *     rather than assumed: a total normalised through "whatever rate was current" is a number
 *     nobody can reproduce next week.
 */
public record PayrollSummary(
        long headcount, Money totalSpend, Money averageSalary, Money medianSalary, LocalDate ratesAsOf) {}
