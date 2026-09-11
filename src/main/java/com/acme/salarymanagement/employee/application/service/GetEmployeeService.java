package com.acme.salarymanagement.employee.application.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acme.salarymanagement.employee.application.port.in.GetEmployee;
import com.acme.salarymanagement.employee.application.port.out.EmployeeRepository;
import com.acme.salarymanagement.employee.application.port.out.EmployeeSummary;
import com.acme.salarymanagement.employee.domain.EmployeeId;

@Service
class GetEmployeeService implements GetEmployee {

    private final EmployeeRepository employees;

    GetEmployeeService(EmployeeRepository employees) {
        this.employees = employees;
    }

    @Override
    @PreAuthorize("hasAnyRole('HR_MANAGER','HR_ANALYST')")
    @Transactional(readOnly = true)
    public EmployeeSummary byId(EmployeeId id) {
        return employees.load(id).map(EmployeeSummary::of).orElseThrow(() -> new EmployeeNotFound(id));
    }
}
