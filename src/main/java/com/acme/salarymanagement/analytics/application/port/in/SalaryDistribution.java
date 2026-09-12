package com.acme.salarymanagement.analytics.application.port.in;

import java.time.LocalDate;
import java.util.List;

import com.acme.salarymanagement.shared.Money;

/**
 * Where each group's salaries sit: quartiles and range, per role or department.
 *
 * @param dimension what the rows are groups of, echoed back so a response can be read on its own
 * @param rows one per group that has at least one employee in it, widest spread first
 * @param ratesAsOf the date whose exchange rates every figure here was converted through
 */
public record SalaryDistribution(DistributionDimension dimension, List<DistributionRow> rows, LocalDate ratesAsOf) {

    public SalaryDistribution {
        rows = List.copyOf(rows);
    }

    /**
     * Every figure is {@code percentile_disc}, so every figure is an amount somebody in the group
     * is actually paid (D135). A median of 113,930.0099 is a float artefact, and a median nobody
     * earns is a worse answer besides.
     *
     * @param group the job title or department this row is for
     * @param headcount how many active employees are in it
     * @param lowest the least-paid person in the group, normalised
     * @param p25 the lower quartile
     * @param median the middle salary
     * @param p75 the upper quartile
     * @param p90 the ninetieth percentile
     * @param highest the best-paid person in the group, normalised
     */
    public record DistributionRow(
            String group, long headcount, Money lowest, Money p25, Money median, Money p75, Money p90, Money highest) {}
}
