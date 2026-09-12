package com.acme.salarymanagement.employee.application.port.out;

import java.util.Optional;

import com.acme.salarymanagement.employee.domain.Employee;
import com.acme.salarymanagement.employee.domain.EmployeeId;
import com.acme.salarymanagement.shared.Money;

/** Loading and storing the aggregate itself, as opposed to reading the directory. */
public interface EmployeeRepository {

    Optional<Employee> load(EmployeeId id);

    /**
     * Writes back the one thing that changes.
     *
     * <p>Named for exactly what it does rather than {@code save}: pay is the only mutable field on
     * the aggregate today, and a method that claims to save an employee while writing two columns
     * is a method someone will later trust with a third.
     */
    /**
     * @param replacing the salary the aggregate was carrying when the change was decided - the
     *     revision's {@code previousAmount}. The write applies only while the stored salary is
     *     still that, so two managers deciding from the same figure cannot both succeed.
     * @throws com.acme.salarymanagement.employee.domain.ConcurrentSalaryChange if it moved
     */
    void saveCurrentSalaryOf(Employee employee, Money replacing);
}
