package com.acme.salarymanagement.employee.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

import com.acme.salarymanagement.shared.CountryCode;
import com.acme.salarymanagement.shared.Money;

/**
 * The aggregate root.
 *
 * <p>Identity is the {@link EmployeeId} alone: an employee is the same employee after a raise. The
 * id is assigned by the application at construction, never by the database, so equality holds for
 * the whole transient life of an unsaved employee - which is what the seed generator relies on
 * while it holds ten thousand of them before the first insert.
 *
 * <p>A class rather than a record because {@code currentSalary} changes. It changes only through
 * the method that also produces the audit record; there is no setter.
 */
public class Employee {

    private final EmployeeId id;
    private final EmployeeNumber employeeNumber;
    private final PersonName name;
    private final EmailAddress email;
    private final CountryCode country;
    private final DepartmentId department;
    private final JobTitle jobTitle;
    private final SeniorityLevel level;
    private final LocalDate hireDate;
    private final EmploymentStatus status;
    private Money currentSalary;

    public Employee(
            EmployeeId id,
            EmployeeNumber employeeNumber,
            PersonName name,
            EmailAddress email,
            CountryCode country,
            DepartmentId department,
            JobTitle jobTitle,
            SeniorityLevel level,
            LocalDate hireDate,
            EmploymentStatus status,
            Money currentSalary) {
        this.id = Objects.requireNonNull(id, "an employee id is required, assigned before persistence");
        this.employeeNumber = Objects.requireNonNull(employeeNumber, "an employee number is required");
        this.name = Objects.requireNonNull(name, "a name is required");
        this.email = Objects.requireNonNull(email, "an email address is required");
        this.country = Objects.requireNonNull(country, "a country is required");
        this.department = Objects.requireNonNull(department, "a department is required");
        this.jobTitle = Objects.requireNonNull(jobTitle, "a job title is required");
        this.level = Objects.requireNonNull(level, "a seniority level is required");
        this.hireDate = Objects.requireNonNull(hireDate, "a hire date is required");
        this.status = Objects.requireNonNull(status, "an employment status is required");
        this.currentSalary = Objects.requireNonNull(currentSalary, "a salary is required");
        requireSalaryMatchesCountry(currentSalary, country);
    }

    /**
     * Invariant I9, enforced at construction as well as on change. A euro salary on an employee in
     * India is not a validation nicety: it is a figure that will be summed into a payroll total and
     * reported to an executive as though it were rupees.
     */
    private static void requireSalaryMatchesCountry(Money salary, CountryCode country) {
        if (!salary.currency().equals(country.currency())) {
            throw new IllegalArgumentException("an employee in %s is paid in %s, but the salary is in %s"
                    .formatted(
                            country.code(),
                            country.currency().code(),
                            salary.currency().code()));
        }
    }

    /**
     * The only way a salary changes. Mutates the current value <em>and</em> returns the revision
     * recording it, so there is no code path that moves pay without producing the evidence.
     *
     * <p>Takes the instant to record rather than a clock: the aggregate never consults time, so the
     * same arguments always produce the same revision. The application layer resolves its injected
     * {@code Clock} at the boundary.
     *
     * <p>Every rejection happens before any assignment. An aggregate that mutates and then throws
     * leaves the caller holding a changed employee and no revision to account for it.
     *
     * @param note free text explaining the change; optional, unlike the reason
     * @return the revision to persist in the same transaction as the employee
     */
    public SalaryRevision changeSalaryTo(
            Money newSalary, ChangeReason reason, UserId actor, String note, Instant changedAt) {
        Objects.requireNonNull(newSalary, "a new salary is required");
        Objects.requireNonNull(reason, "a change reason is required: an unexplained change is not an audit record");
        Objects.requireNonNull(actor, "the user making the change is required");
        Objects.requireNonNull(changedAt, "the instant of the change is required");

        if (status == EmploymentStatus.TERMINATED) {
            throw new IllegalStateException(
                    "employee %s is TERMINATED; pay cannot change".formatted(employeeNumber.value()));
        }
        requireSalaryMatchesCountry(newSalary, country);
        if (!newSalary.isPositive()) {
            throw new IllegalArgumentException("a salary must be greater than zero");
        }
        if (newSalary.equals(currentSalary)) {
            throw new IllegalArgumentException(
                    "employee %s is already paid that amount".formatted(employeeNumber.value()));
        }

        String recordedNote = (note == null || note.isBlank()) ? null : note.trim();
        Money previousAmount = currentSalary;
        currentSalary = newSalary;
        return new SalaryRevision(id, previousAmount, newSalary, reason, actor, changedAt, recordedNote);
    }

    public EmployeeId id() {
        return id;
    }

    public EmployeeNumber employeeNumber() {
        return employeeNumber;
    }

    public PersonName name() {
        return name;
    }

    public EmailAddress email() {
        return email;
    }

    public CountryCode country() {
        return country;
    }

    public DepartmentId department() {
        return department;
    }

    public JobTitle jobTitle() {
        return jobTitle;
    }

    public SeniorityLevel level() {
        return level;
    }

    public LocalDate hireDate() {
        return hireDate;
    }

    public EmploymentStatus status() {
        return status;
    }

    public Money currentSalary() {
        return currentSalary;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Employee that)) {
            return false;
        }
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Employee[%s %s]".formatted(employeeNumber.value(), id.value());
    }
}
