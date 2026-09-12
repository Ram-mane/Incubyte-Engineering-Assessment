package com.acme.salarymanagement.analytics.application.port.in;

/**
 * The dimensions payroll spend can be broken down by.
 *
 * <p>An enum rather than a column name for a reason beyond tidiness: a group-by column cannot be a
 * bound parameter, so the only safe way to vary it is a closed set the adapter translates. A string
 * arriving from the query string and reaching {@code GROUP BY} would be the one place in this
 * codebase where SQL is assembled from input.
 */
public enum BreakdownDimension {
    DEPARTMENT,
    COUNTRY,
    LEVEL
}
