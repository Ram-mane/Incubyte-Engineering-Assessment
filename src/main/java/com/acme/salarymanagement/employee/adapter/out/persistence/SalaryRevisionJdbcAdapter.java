package com.acme.salarymanagement.employee.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.acme.salarymanagement.employee.application.port.in.SalaryRevisionView;
import com.acme.salarymanagement.employee.application.port.out.SalaryRevisionLog;
import com.acme.salarymanagement.employee.domain.ChangeReason;
import com.acme.salarymanagement.employee.domain.EmployeeId;
import com.acme.salarymanagement.employee.domain.SalaryRevision;
import com.acme.salarymanagement.employee.domain.UserId;
import com.acme.salarymanagement.shared.CurrencyCode;
import com.acme.salarymanagement.shared.Money;

/**
 * Writes the revision the aggregate produced, and reads an employee's log back.
 *
 * <p>It persists what it is given. It does not build a revision from an employee's before and
 * after, and could not usefully be asked to: the only object that can produce one is
 * {@code changeSalaryTo}, which is the only thing allowed to move pay. An ArchUnit rule keeps it
 * that way.
 *
 * <p>The id is minted here rather than in the domain (D094). The aggregate stays a pure function
 * of its arguments, and a surrogate key exists for keyset paging through a long history.
 */
@Repository
class SalaryRevisionJdbcAdapter implements SalaryRevisionLog {

    private static final String INSERT =
            """
            INSERT INTO salary_revision (id, employee_id, previous_amount, new_amount, currency_code,
                                         change_reason, changed_by, changed_at, note)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    /**
     * Ordered exactly as {@code ix_revision_employee} is, so the newest-first read is an index
     * scan and not a sort of everything the person has ever been paid.
     */
    private static final String NEWEST_FIRST =
            """
            SELECT previous_amount, new_amount, currency_code, change_reason, changed_by, changed_at, note
            FROM   salary_revision
            WHERE  employee_id = ?
            ORDER BY changed_at DESC, id DESC
            LIMIT  ?
            """;

    private static final RowMapper<SalaryRevisionView> AS_VIEW = (row, number) -> {
        CurrencyCode currency = new CurrencyCode(row.getString("currency_code"));
        return new SalaryRevisionView(
                Money.of(row.getBigDecimal("previous_amount"), currency),
                Money.of(row.getBigDecimal("new_amount"), currency),
                ChangeReason.valueOf(row.getString("change_reason")),
                new UserId(row.getObject("changed_by", UUID.class)),
                row.getObject("changed_at", java.time.OffsetDateTime.class).toInstant(),
                row.getString("note"));
    };

    private final JdbcTemplate jdbc;

    SalaryRevisionJdbcAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void append(SalaryRevision revision) {
        jdbc.update(
                INSERT,
                UUID.randomUUID(),
                revision.employeeId().value(),
                revision.previousAmount().amount(),
                revision.newAmount().amount(),
                revision.newAmount().currency().code(),
                revision.reason().name(),
                revision.changedBy().value(),
                // The instant the aggregate was handed, not the one this line could have read.
                java.sql.Timestamp.from(revision.changedAt()),
                revision.note());
    }

    @Override
    public List<SalaryRevisionView> of(EmployeeId employeeId, int limit) {
        return jdbc.query(NEWEST_FIRST, AS_VIEW, employeeId.value(), limit);
    }
}
