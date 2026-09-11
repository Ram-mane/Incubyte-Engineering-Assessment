package com.acme.salarymanagement.employee.application.service;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acme.salarymanagement.employee.application.port.in.GetSalaryRevisions;
import com.acme.salarymanagement.employee.application.port.in.SalaryRevisionView;
import com.acme.salarymanagement.employee.application.port.out.SalaryRevisionLog;
import com.acme.salarymanagement.employee.domain.EmployeeId;

@Service
class GetSalaryRevisionsService implements GetSalaryRevisions {

    static final int LONGEST_LOG = 200;

    private static final int SHORTEST_LOG = 1;

    private final SalaryRevisionLog revisions;

    GetSalaryRevisionsService(SalaryRevisionLog revisions) {
        this.revisions = revisions;
    }

    @Override
    @PreAuthorize("hasAnyRole('HR_MANAGER','HR_ANALYST')")
    @Transactional(readOnly = true)
    public List<SalaryRevisionView> of(EmployeeId employeeId, int limit) {
        if (limit < SHORTEST_LOG) {
            throw new IllegalArgumentException("a log holds at least one revision");
        }
        return revisions.of(employeeId, Math.min(limit, LONGEST_LOG));
    }
}
