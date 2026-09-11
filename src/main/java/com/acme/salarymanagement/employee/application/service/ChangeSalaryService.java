package com.acme.salarymanagement.employee.application.service;

import java.time.Clock;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acme.salarymanagement.employee.application.port.in.ChangeSalary;
import com.acme.salarymanagement.employee.application.port.in.ChangeSalaryCommand;
import com.acme.salarymanagement.employee.application.port.out.ActingUsers;
import com.acme.salarymanagement.employee.application.port.out.EmployeeRepository;
import com.acme.salarymanagement.employee.application.port.out.EmployeeSummary;
import com.acme.salarymanagement.employee.application.port.out.SalaryRevisionLog;
import com.acme.salarymanagement.employee.domain.Employee;
import com.acme.salarymanagement.employee.domain.SalaryRevision;

/**
 * Moves pay, and records that it moved, or does neither.
 *
 * <p>The two writes are one transaction. This is the whole project's headline invariant arriving
 * at the layer where it can still be lost: the aggregate makes it impossible to change pay
 * <em>without producing</em> a revision, and nothing below the aggregate makes it impossible to
 * persist the first write and not the second. A partial commit would leave a salary nobody can
 * account for, and every happy-path test would still pass.
 *
 * <p>Time enters here and nowhere deeper. The clock is injected and resolved to an {@code Instant}
 * at this boundary, which is the one place a clock belongs: the aggregate is handed the instant to
 * record, so the same call always produces the same revision.
 */
@Service
class ChangeSalaryService implements ChangeSalary {

    private final EmployeeRepository employees;
    private final SalaryRevisionLog revisions;
    private final ActingUsers actingUsers;
    private final Clock clock;

    ChangeSalaryService(
            EmployeeRepository employees, SalaryRevisionLog revisions, ActingUsers actingUsers, Clock clock) {
        this.employees = employees;
        this.revisions = revisions;
        this.actingUsers = actingUsers;
        this.clock = clock;
    }

    @Override
    @PreAuthorize("hasRole('HR_MANAGER')")
    @Transactional
    public EmployeeSummary change(ChangeSalaryCommand command) {
        // Checked rather than trusted, and never defaulted: a revision attributed to a user who
        // does not exist, or to an invented system account, answers "who changed this" with a
        // fiction. The column is NOT NULL with a foreign key for the same reason.
        if (!actingUsers.exists(command.actor())) {
            throw new IllegalArgumentException("no such user is making this change");
        }

        Employee employee =
                employees.load(command.employeeId()).orElseThrow(() -> new EmployeeNotFound(command.employeeId()));

        SalaryRevision revision = employee.changeSalaryTo(
                command.newSalary(), command.reason(), command.actor(), command.note(), clock.instant());

        employees.saveCurrentSalaryOf(employee);
        revisions.append(revision);

        return EmployeeSummary.of(employee);
    }
}
