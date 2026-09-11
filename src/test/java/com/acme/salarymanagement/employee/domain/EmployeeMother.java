package com.acme.salarymanagement.employee.domain;

import java.time.LocalDate;

import com.acme.salarymanagement.shared.CountryCode;
import com.acme.salarymanagement.shared.CurrencyCode;
import com.acme.salarymanagement.shared.Department;
import com.acme.salarymanagement.shared.JobTitle;
import com.acme.salarymanagement.shared.Money;
import com.acme.salarymanagement.shared.SeniorityLevel;

/**
 * Object Mother for employees, so a test reads as the scenario it describes rather than as
 * fourteen constructor arguments - docs/07-TEST-STRATEGY.md section 8.
 *
 * <p>Every value is explicit. Nothing here is random and nothing reads a clock: a test that fails
 * only on some runs is worse than no test.
 */
final class EmployeeMother {

    private EmployeeId id = EmployeeId.newId();
    private EmployeeNumber number = new EmployeeNumber("E00042");
    private PersonName name = new PersonName("Alice", "Kapoor");
    private EmailAddress email = new EmailAddress("alice.kapoor@acme.test");
    private CountryCode country = new CountryCode("IN");
    private LocalDate hireDate = LocalDate.of(2019, 4, 1);
    private Department department = new Department("Engineering");
    private JobTitle jobTitle = new JobTitle("Software Engineer");
    private SeniorityLevel level = SeniorityLevel.SENIOR;
    private EmploymentStatus status = EmploymentStatus.ACTIVE;
    private Money salary = Money.of("1200000.00", new CurrencyCode("INR"));

    private EmployeeMother() {}

    static EmployeeMother anEmployee() {
        return new EmployeeMother();
    }

    EmployeeMother withId(EmployeeId value) {
        this.id = value;
        return this;
    }

    EmployeeMother withNumber(EmployeeNumber value) {
        this.number = value;
        return this;
    }

    EmployeeMother inIndia() {
        this.country = new CountryCode("IN");
        this.salary = Money.of("1200000.00", new CurrencyCode("INR"));
        return this;
    }

    EmployeeMother inGermany() {
        this.country = new CountryCode("DE");
        this.salary = Money.of("85000.00", new CurrencyCode("EUR"));
        return this;
    }

    EmployeeMother inNoCountry() {
        this.country = null;
        return this;
    }

    EmployeeMother earning(String amount) {
        this.salary = Money.of(amount, country.currency());
        return this;
    }

    EmployeeMother earningExactly(com.acme.salarymanagement.shared.Money value) {
        this.salary = value;
        return this;
    }

    EmployeeMother earningEuros(String amount) {
        this.salary = Money.of(amount, new CurrencyCode("EUR"));
        return this;
    }

    EmployeeMother earningNothing() {
        this.salary = null;
        return this;
    }

    EmployeeMother hiredOn(LocalDate value) {
        this.hireDate = value;
        return this;
    }

    EmployeeMother inEngineering() {
        this.department = new Department("Engineering");
        return this;
    }

    EmployeeMother inNoDepartment() {
        this.department = null;
        return this;
    }

    EmployeeMother asSeniorEngineer() {
        this.jobTitle = new JobTitle("Software Engineer");
        this.level = SeniorityLevel.SENIOR;
        return this;
    }

    EmployeeMother withNoJobTitle() {
        this.jobTitle = null;
        return this;
    }

    EmployeeMother atNoLevel() {
        this.level = null;
        return this;
    }

    EmployeeMother status(EmploymentStatus value) {
        this.status = value;
        return this;
    }

    EmployeeMother terminated() {
        this.status = EmploymentStatus.TERMINATED;
        return this;
    }

    Employee build() {
        return new Employee(id, number, name, email, country, department, jobTitle, level, hireDate, status, salary);
    }
}
