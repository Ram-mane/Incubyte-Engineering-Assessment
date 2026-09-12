package com.acme.salarymanagement.employee.domain;

/**
 * The salary moved between this change being decided and being written.
 *
 * <p>Two managers open the same employee on 100,000 and each sets a new figure. Both revisions say
 * "from 100,000"; only one of them is true. Losing the second write is a nuisance - recording it
 * is a lie, because the audit log would then describe a step the pay never took, and the log is
 * the one thing this system exists to be trusted about.
 *
 * <p>Names no amount (D080): the caller reloads and sees the current figure through the ordinary
 * read path.
 */
public class ConcurrentSalaryChange extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ConcurrentSalaryChange(EmployeeId employee) {
        super(("This employee's pay changed while you were deciding, so the change was not applied. "
                        + "Reload employee %s and decide again against the salary now on record.")
                .formatted(employee.value()));
    }
}
