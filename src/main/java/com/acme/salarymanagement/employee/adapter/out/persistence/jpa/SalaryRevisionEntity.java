package com.acme.salarymanagement.employee.adapter.out.persistence.jpa;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * The audit log, mapped for reading only.
 *
 * <p>This is the JPA slice ADR-0014 scheduled: one <b>read</b> path, on a table the write path
 * never touches through this mapping. There is no setter and nothing here is ever attached to a
 * dirty-checking flush, because the whole reason writes stay on JdbcTemplate is that an ORM would
 * make an unrecorded pay change the default rather than the impossible.
 *
 * <p>{@code changedBy} is a lazy {@code ManyToOne} deliberately: rendering who made each change
 * then costs one query per row unless the read asks for it up front, which is exactly the N+1 this
 * slice exists to produce, measure and fix. See {@code docs/evidence/09-jpa-read-path.txt}.
 */
@Entity
@Table(name = "salary_revision")
class SalaryRevisionEntity {

    @Id
    private UUID id;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "previous_amount", nullable = false)
    private BigDecimal previousAmount;

    @Column(name = "new_amount", nullable = false)
    private BigDecimal newAmount;

    @Column(name = "currency_code", nullable = false, columnDefinition = "bpchar")
    private String currencyCode;

    @Column(name = "change_reason", nullable = false)
    private String changeReason;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    @Column(name = "note")
    private String note;

    /** The user who made the change. Lazy: see the class comment. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "changed_by", nullable = false)
    private AppUserEntity changedBy;

    protected SalaryRevisionEntity() {
        // for JPA
    }

    UUID id() {
        return id;
    }

    UUID employeeId() {
        return employeeId;
    }

    BigDecimal previousAmount() {
        return previousAmount;
    }

    BigDecimal newAmount() {
        return newAmount;
    }

    String currencyCode() {
        return currencyCode == null ? null : currencyCode.trim();
    }

    String changeReason() {
        return changeReason;
    }

    Instant changedAt() {
        return changedAt;
    }

    String note() {
        return note;
    }

    AppUserEntity changedBy() {
        return changedBy;
    }
}
