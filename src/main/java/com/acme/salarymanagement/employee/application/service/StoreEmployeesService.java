package com.acme.salarymanagement.employee.application.service;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acme.salarymanagement.employee.application.port.in.StoreEmployees;
import com.acme.salarymanagement.employee.application.port.out.EmployeeWriteRepository;
import com.acme.salarymanagement.employee.domain.Employee;
import com.acme.salarymanagement.employee.domain.SalaryRevision;

/**
 * Profile-scoped because the only bulk writer today is the seed, and the pool it writes through
 * is a privilege the deployed application does not have (D103). When CSV import arrives it brings
 * its own entry point rather than widening this one.
 */
@Service
@Profile("seed")
class StoreEmployeesService implements StoreEmployees {

    private final EmployeeWriteRepository employees;

    StoreEmployeesService(EmployeeWriteRepository employees) {
        this.employees = employees;
    }

    @Override
    @Transactional
    public void replaceEveryoneWith(List<Employee> population, List<SalaryRevision> history) {
        // One transaction: a seed that half-loads leaves a directory nobody can trust, and the
        // demo would show ten thousand rows that are not the ten thousand the test asserted.
        employees.deleteEveryone();
        employees.saveAll(population);
        // After the employees, because every revision references one.
        employees.appendAll(history);
    }
}
