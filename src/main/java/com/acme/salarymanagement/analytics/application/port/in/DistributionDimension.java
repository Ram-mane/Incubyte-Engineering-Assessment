package com.acme.salarymanagement.analytics.application.port.in;

/**
 * What a salary distribution is grouped by.
 *
 * <p>Its own enum rather than {@link BreakdownDimension}: the two questions take different sets.
 * "Spend by country" is a question an HR manager asks; "the quartiles of a country" is not one this
 * product answers, because a country is not a peer group. Merging the enums would make the type
 * accept a dimension the screen has no meaning for.
 */
public enum DistributionDimension {
    JOB_TITLE,
    DEPARTMENT
}
