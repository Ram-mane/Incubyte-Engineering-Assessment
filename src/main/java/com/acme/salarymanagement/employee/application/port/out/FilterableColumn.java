package com.acme.salarymanagement.employee.application.port.out;

/**
 * The columns a directory filter may read distinct values from.
 *
 * <p>An enum rather than a column name the caller supplies: the adapter maps each constant to a
 * fixed piece of SQL, so there is no path by which a request becomes part of a statement.
 */
public enum FilterableColumn {
    COUNTRY,
    DEPARTMENT,
    JOB_TITLE
}
