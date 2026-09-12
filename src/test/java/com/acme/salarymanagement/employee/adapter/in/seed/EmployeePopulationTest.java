package com.acme.salarymanagement.employee.adapter.in.seed;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.acme.salarymanagement.employee.domain.Employee;
import com.acme.salarymanagement.employee.domain.UserId;

/**
 * The seed is data three different things depend on agreeing about: the demo, the tests and the
 * load test. So what it generates is asserted, not eyeballed once.
 */
class EmployeePopulationTest {

    private static final int TEN_THOUSAND = 10_000;

    @Test
    void the_same_seed_produces_the_same_org_every_time() {
        var first = EmployeePopulation.of(500);
        var second = EmployeePopulation.of(500);

        assertThat(describe(first))
                .as("a number quoted from the demo must be the number the load test measured")
                .isEqualTo(describe(second));
    }

    @Test
    void every_employee_number_is_someone_else() {
        var population = EmployeePopulation.of(TEN_THOUSAND);

        assertThat(population.stream().map(e -> e.employeeNumber().value()).distinct())
                .as("employee_number is UNIQUE in the schema; a collision fails the seed mid-batch")
                .hasSize(TEN_THOUSAND);
    }

    @Test
    void every_email_belongs_to_one_person() {
        var population = EmployeePopulation.of(TEN_THOUSAND);

        assertThat(population.stream().map(e -> e.email().value()).distinct())
                .as("thirty given names and twenty-six family names cannot spell ten thousand "
                        + "distinct addresses on their own")
                .hasSize(TEN_THOUSAND);
    }

    @Test
    void everyone_is_paid_in_the_currency_of_the_country_they_work_in() {
        var population = EmployeePopulation.of(TEN_THOUSAND);

        assertThat(population)
                .allSatisfy(employee -> assertThat(employee.currentSalary().currency())
                        .isEqualTo(employee.country().currency()));
    }

    @Test
    void the_org_spans_every_market_the_dashboard_has_to_normalise() {
        var population = EmployeePopulation.of(TEN_THOUSAND);

        assertThat(population.stream().map(e -> e.country().code()).distinct())
                .as("six currencies is what makes the FX normalisation worth writing")
                .containsExactlyInAnyOrder("IN", "US", "DE", "GB", "SG", "AU");
    }

    @Test
    void nobody_is_paid_a_salary_that_could_not_be_stored() {
        // Employee's constructor rejects a non-positive salary, so a generator edge that produced
        // one would fail here rather than part way through inserting ten thousand rows.
        assertThat(EmployeePopulation.of(TEN_THOUSAND))
                .allSatisfy(employee ->
                        assertThat(employee.currentSalary().isPositive()).isTrue());
    }

    @Test
    void everyone_has_a_history_and_every_entry_of_it_was_made_by_the_aggregate() {
        var org = EmployeePopulation.withHistory(
                200, new UserId(UUID.fromString("00000000-0000-4000-8000-00000000ffff")));

        assertThat(org.revisions()).hasSizeGreaterThanOrEqualTo(200 * 2);
        assertThat(org.revisions())
                .as("a revision that does not move the salary is one changeSalaryTo would refuse")
                .allSatisfy(revision -> assertThat(revision.newAmount()).isNotEqualTo(revision.previousAmount()));
    }

    @Test
    void an_employees_final_salary_is_where_their_own_history_ended() {
        var org =
                EmployeePopulation.withHistory(50, new UserId(UUID.fromString("00000000-0000-4000-8000-00000000ffff")));
        var first = org.employees().get(0);
        var theirs = org.revisions().stream()
                .filter(revision -> revision.employeeId().equals(first.id()))
                .toList();

        assertThat(first.currentSalary())
                .as("the figure and the history that explains it must be the same story")
                .isEqualTo(theirs.get(theirs.size() - 1).newAmount());
    }

    private static List<String> describe(List<Employee> population) {
        return population.stream()
                .map(employee -> "%s|%s|%s|%s|%s|%s"
                        .formatted(
                                employee.employeeNumber().value(),
                                employee.email().value(),
                                employee.country().code(),
                                employee.department().value(),
                                employee.level(),
                                employee.currentSalary()))
                .toList();
    }
}
