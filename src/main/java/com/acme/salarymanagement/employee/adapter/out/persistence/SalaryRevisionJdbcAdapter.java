package com.acme.salarymanagement.employee.adapter.out.persistence;

import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.acme.salarymanagement.employee.application.port.out.SalaryRevisionLog;
import com.acme.salarymanagement.employee.domain.SalaryRevision;

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
}
