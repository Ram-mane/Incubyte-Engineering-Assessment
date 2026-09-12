package com.acme.salarymanagement.analytics.application.port.in;

import java.time.LocalDate;
import java.util.List;

import com.acme.salarymanagement.shared.Money;

/**
 * Spend and headcount per group, largest first.
 *
 * @param dimension what the rows are groups of, echoed back so a response can be read on its own
 * @param rows one per group that has at least one employee in it. A filter nobody matches returns
 *     none: there is no group to name, and inventing a zero row would assert that one exists.
 * @param ratesAsOf the date whose exchange rates every figure here was converted through
 */
public record PayrollBreakdown(BreakdownDimension dimension, List<BreakdownRow> rows, LocalDate ratesAsOf) {

    public PayrollBreakdown {
        rows = List.copyOf(rows);
    }

    /**
     * @param group the department, country or level this row is for
     * @param headcount how many active employees are in it
     * @param totalSpend their salaries, normalised to the reporting currency
     * @param averageSalary the mean of those normalised amounts, not of the local ones
     */
    public record BreakdownRow(String group, long headcount, Money totalSpend, Money averageSalary) {}
}
