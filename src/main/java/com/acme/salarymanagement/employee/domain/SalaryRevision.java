package com.acme.salarymanagement.employee.domain;

import java.time.Instant;

import com.acme.salarymanagement.shared.Money;

/**
 * The audit record of one salary change, returned by {@link Employee#changeSalaryTo} to be
 * persisted in the same transaction.
 *
 * <p>It carries no identity, deliberately. A revision is never looked up, updated, deleted or
 * referenced by anything - the only endpoint that reads one lists it - so it is a value, and the
 * database gives it a surrogate key for keyset paging. Entities that are referenced need identity;
 * append-only records that are only ever listed do not.
 *
 * <p>That absence is what keeps {@code changeSalaryTo} a pure function of its arguments. Minting a
 * UUID inside the aggregate would make the same call return something different each time, and
 * would break the seed generator's promise of identical data across runs
 * (05-DATA-MODEL.md section 6) for some thirty thousand revisions.
 */
public record SalaryRevision(
        EmployeeId employeeId,
        Money previousAmount,
        Money newAmount,
        ChangeReason reason,
        UserId changedBy,
        Instant changedAt,
        String note) {}
