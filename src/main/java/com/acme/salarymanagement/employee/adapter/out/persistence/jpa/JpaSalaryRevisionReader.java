package com.acme.salarymanagement.employee.adapter.out.persistence.jpa;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;

import com.acme.salarymanagement.employee.application.port.in.SalaryRevisionView;
import com.acme.salarymanagement.employee.application.port.out.SalaryRevisionReader;
import com.acme.salarymanagement.employee.domain.ChangeReason;
import com.acme.salarymanagement.employee.domain.EmployeeId;
import com.acme.salarymanagement.employee.domain.UserId;
import com.acme.salarymanagement.shared.CurrencyCode;
import com.acme.salarymanagement.shared.Money;

/**
 * Reads the audit log through JPA, with the association fetched up front.
 *
 * <p>The naive finder beside it costs one statement per row as soon as the email is read - 31
 * statements for a thirty-row log. The entity graph makes it one. Both numbers are measured and
 * committed in {@code docs/evidence/09-jpa-read-path.txt}.
 */
@Repository
class JpaSalaryRevisionReader implements SalaryRevisionReader {

    private final SalaryRevisionRows revisions;

    JpaSalaryRevisionReader(SalaryRevisionRows revisions) {
        this.revisions = revisions;
    }

    @Override
    public List<SalaryRevisionView> of(EmployeeId employeeId, int limit) {
        return revisions.findEagerlyByEmployeeIdOrderByChangedAtDesc(employeeId.value(), Limit.of(limit)).stream()
                .map(JpaSalaryRevisionReader::asView)
                .toList();
    }

    /** The same read without the entity graph. Exists to be measured, and is called by one test. */
    List<SalaryRevisionView> withoutTheEntityGraph(EmployeeId employeeId, int limit) {
        return revisions.findByEmployeeIdOrderByChangedAtDesc(employeeId.value(), Limit.of(limit)).stream()
                .map(JpaSalaryRevisionReader::asView)
                .toList();
    }

    private static SalaryRevisionView asView(SalaryRevisionEntity revision) {
        CurrencyCode currency = new CurrencyCode(revision.currencyCode());
        return new SalaryRevisionView(
                money(revision.previousAmount(), currency),
                money(revision.newAmount(), currency),
                ChangeReason.valueOf(revision.changeReason()),
                new UserId(revision.changedBy().id()),
                // Reading the email is what makes the association load. Without it the lazy proxy
                // is never touched and the N+1 does not appear - which is why the naive finder
                // looks fine until somebody renders who made the change.
                revision.changedBy().email(),
                revision.changedAt(),
                revision.note());
    }

    private static Money money(BigDecimal amount, CurrencyCode currency) {
        return Money.of(amount, currency);
    }
}
