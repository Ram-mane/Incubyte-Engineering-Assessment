package com.acme.salarymanagement.employee.domain;

import static com.acme.salarymanagement.employee.domain.EmployeeMother.anEmployee;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.acme.salarymanagement.shared.CountryCode;
import com.acme.salarymanagement.shared.CurrencyCode;
import com.acme.salarymanagement.shared.JobTitle;
import com.acme.salarymanagement.shared.Money;
import com.acme.salarymanagement.shared.SeniorityLevel;

/**
 * The aggregate root. What it refuses to be built as matters as much as what it holds: an employee
 * whose salary is in the wrong currency is not a validation nicety, it is a payroll figure that
 * will be summed into a total and reported to an executive.
 */
class EmployeeTest {

    @Test
    void an_employee_is_paid_in_the_currency_of_the_country_they_work_in() {
        var alice = anEmployee().inIndia().earning("1200000.00").build();

        assertThat(alice.currentSalary()).isEqualTo(Money.of("1200000.00", new CurrencyCode("INR")));
    }

    @Test
    void a_salary_in_a_currency_other_than_the_country_currency_is_rejected() {
        // I9. Germany pays in EUR; a euro salary for an Indian employee would be summed into the
        // payroll total as though it were rupees.
        assertThatThrownBy(() -> anEmployee().inIndia().earningEuros("45000.00").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("IN")
                .hasMessageContaining("INR")
                .hasMessageContaining("EUR");
    }

    @Test
    void an_employee_starts_in_the_status_they_are_created_with() {
        assertThat(anEmployee().build().status()).isEqualTo(EmploymentStatus.ACTIVE);
        assertThat(anEmployee().terminated().build().status()).isEqualTo(EmploymentStatus.TERMINATED);
    }

    @Test
    void two_employees_with_the_same_identity_are_the_same_employee() {
        // Identity, not attributes: an aggregate is the same aggregate after its salary changes.
        var id = EmployeeId.newId();
        var alice = anEmployee().withId(id).earning("1200000.00").build();
        var alsoAlice = anEmployee().withId(id).earning("1380000.00").build();

        assertThat(alice).isEqualTo(alsoAlice);
        assertThat(alice).hasSameHashCodeAs(alsoAlice);
    }

    @Test
    void two_employees_with_different_identities_are_different_employees() {
        assertThat(anEmployee().build()).isNotEqualTo(anEmployee().build());
    }

    @Test
    void an_employee_without_an_identity_is_rejected() {
        assertThatThrownBy(() -> anEmployee().withId(null).build()).isInstanceOf(NullPointerException.class);
    }

    @Test
    void an_employee_without_an_employee_number_is_rejected() {
        assertThatThrownBy(() -> anEmployee().withNumber(null).build()).isInstanceOf(NullPointerException.class);
    }

    @Test
    void an_employee_without_a_salary_is_rejected() {
        assertThatThrownBy(() -> anEmployee().earningNothing().build()).isInstanceOf(NullPointerException.class);
    }

    @Test
    void an_employee_without_a_country_is_rejected() {
        assertThatThrownBy(() -> anEmployee().inNoCountry().build()).isInstanceOf(NullPointerException.class);
    }

    @Test
    void an_employee_hired_without_a_date_is_rejected() {
        assertThatThrownBy(() -> anEmployee().hiredOn(null).build()).isInstanceOf(NullPointerException.class);
    }

    // The three confirmed filter dimensions (D010): every directory and dashboard query groups
    // or filters on them, so an employee missing one cannot appear in a total that claims to be
    // complete.

    @Test
    void an_employee_without_a_department_is_rejected() {
        assertThatThrownBy(() -> anEmployee().inNoDepartment().build()).isInstanceOf(NullPointerException.class);
    }

    @Test
    void an_employee_without_a_job_title_is_rejected() {
        assertThatThrownBy(() -> anEmployee().withNoJobTitle().build()).isInstanceOf(NullPointerException.class);
    }

    @Test
    void an_employee_without_a_seniority_level_is_rejected() {
        assertThatThrownBy(() -> anEmployee().atNoLevel().build()).isInstanceOf(NullPointerException.class);
    }

    @Test
    void an_employee_carries_the_details_it_was_created_with() {
        var alice = anEmployee().build();

        assertThat(alice.employeeNumber()).isEqualTo(new EmployeeNumber("E00042"));
        assertThat(alice.name()).isEqualTo(new PersonName("Alice", "Kapoor"));
        assertThat(alice.email()).isEqualTo(new EmailAddress("alice.kapoor@acme.test"));
        assertThat(alice.hireDate()).isEqualTo(LocalDate.of(2019, 4, 1));
        assertThat(alice.country()).isEqualTo(new CountryCode("IN"));
    }

    @Test
    void an_employee_carries_the_dimensions_the_dashboard_filters_on() {
        var alice = anEmployee().inEngineering().asSeniorEngineer().build();

        assertThat(alice.department()).isEqualTo(new DepartmentId(3L));
        assertThat(alice.jobTitle()).isEqualTo(new JobTitle("Software Engineer"));
        assertThat(alice.level()).isEqualTo(SeniorityLevel.SENIOR);
    }
}
