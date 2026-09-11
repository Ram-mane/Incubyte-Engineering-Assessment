package com.acme.salarymanagement.employee.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.acme.salarymanagement.employee.application.port.in.ChangeSalary;
import com.acme.salarymanagement.employee.application.port.in.ChangeSalaryCommand;
import com.acme.salarymanagement.employee.application.port.in.GetSalaryRevisions;
import com.acme.salarymanagement.employee.application.port.in.SalaryRevisionView;
import com.acme.salarymanagement.employee.application.port.out.EmployeeSummary;
import com.acme.salarymanagement.employee.application.service.EmployeeNotFound;
import com.acme.salarymanagement.employee.domain.ChangeReason;
import com.acme.salarymanagement.employee.domain.UserId;
import com.acme.salarymanagement.shared.CountryCode;
import com.acme.salarymanagement.shared.CurrencyCode;
import com.acme.salarymanagement.shared.Department;
import com.acme.salarymanagement.shared.JobTitle;
import com.acme.salarymanagement.shared.Money;
import com.acme.salarymanagement.shared.SeniorityLevel;

/** The pay-change endpoint and the log endpoint, at the level of the documents they exchange. */
@WebMvcTest({SalaryController.class})
@Import({SalaryControllerTest.AUseCaseThatRemembers.class, ProblemHandler.class})
class SalaryControllerTest {

    private static final UUID EMPLOYEE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ACTOR = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID ABSENTEE = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final CurrencyCode INR = new CurrencyCode("INR");

    static final AtomicReference<ChangeSalaryCommand> LAST_COMMAND = new AtomicReference<>();

    @Autowired
    private MockMvc mvc;

    @Test
    void a_pay_change_returns_the_employee_as_they_now_are() throws Exception {
        mvc.perform(
                        put("/api/v1/employees/{id}/salary", EMPLOYEE)
                                .with(jwt().jwt(token -> token.subject(ACTOR.toString())))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"amount":"1380000.00","currency":"INR","reason":"MERIT",
                                 "note":"Annual merit review"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.salary.amount").value("1380000.00"))
                .andExpect(jsonPath("$.salary.amount").isString())
                .andExpect(jsonPath("$.salary.currency").value("INR"));
    }

    @Test
    void the_reason_the_actor_and_the_note_all_reach_the_use_case() throws Exception {
        mvc.perform(
                        put("/api/v1/employees/{id}/salary", EMPLOYEE)
                                .with(jwt().jwt(token -> token.subject(ACTOR.toString())))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"amount":"1380000.00","currency":"INR","reason":"PROMOTION",
                                 "note":"Promoted to lead"}
                                """))
                .andExpect(status().isOk());

        var command = LAST_COMMAND.get();
        assertThat(command.reason()).isEqualTo(ChangeReason.PROMOTION);
        assertThat(command.actor()).isEqualTo(new UserId(ACTOR));
        assertThat(command.note()).isEqualTo("Promoted to lead");
        assertThat(command.newSalary()).isEqualTo(Money.of("1380000.00", INR));
    }

    @Test
    void a_change_without_a_reason_is_refused_before_it_reaches_the_domain() throws Exception {
        mvc.perform(put("/api/v1/employees/{id}/salary", EMPLOYEE)
                        .with(jwt().jwt(token -> token.subject(ACTOR.toString())))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"amount":"1380000.00","currency":"INR","actorId":"%s"}
                                """
                                        .formatted(ACTOR)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void the_actor_comes_from_the_token_and_cannot_be_named_in_the_body() throws Exception {
        mvc.perform(
                        put("/api/v1/employees/{id}/salary", EMPLOYEE)
                                .with(jwt().jwt(token -> token.subject(ACTOR.toString())))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                // Somebody else's id, offered in the body. It is not a field of the
                                // request, so it is not read: a caller cannot launder a pay change
                                // through another person's name.
                                .content(
                                        """
                                {"amount":"1380000.00","currency":"INR","reason":"MERIT",
                                 "actorId":"99999999-9999-4999-8999-999999999999"}
                                """))
                .andExpect(status().isOk());

        assertThat(LAST_COMMAND.get().actor()).isEqualTo(new UserId(ACTOR));
    }

    @Test
    void changing_the_pay_of_somebody_who_is_not_here_is_a_404() throws Exception {
        mvc.perform(put("/api/v1/employees/{id}/salary", ABSENTEE)
                        .with(jwt().jwt(token -> token.subject(ACTOR.toString())))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"amount":"1380000.00","currency":"INR","reason":"MERIT","actorId":"%s"}
                                """
                                        .formatted(ACTOR)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("No such employee"));
    }

    @Test
    void the_log_comes_back_newest_first_with_both_amounts() throws Exception {
        mvc.perform(get("/api/v1/employees/{id}/salary-revisions", EMPLOYEE)
                        .with(jwt().jwt(token -> token.subject(ACTOR.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].newAmount.amount").value("1380000.00"))
                .andExpect(jsonPath("$[0].previousAmount.amount").value("1200000.00"))
                .andExpect(jsonPath("$[0].reason").value("MERIT"))
                .andExpect(jsonPath("$[0].changedBy").value(ACTOR.toString()))
                .andExpect(jsonPath("$[1].newAmount.amount").value("1200000.00"));
    }

    @TestConfiguration
    static class AUseCaseThatRemembers {

        @Bean
        ChangeSalary changeSalary() {
            return command -> {
                if (command.employeeId().value().equals(ABSENTEE)) {
                    throw new EmployeeNotFound(command.employeeId());
                }
                LAST_COMMAND.set(command);
                return new EmployeeSummary(
                        EMPLOYEE,
                        "E00042",
                        "Alice",
                        "Kapoor",
                        "alice.kapoor@acme.test",
                        new CountryCode("IN"),
                        new Department("Engineering"),
                        new JobTitle("Software Engineer"),
                        SeniorityLevel.SENIOR,
                        command.newSalary());
            };
        }

        @Bean
        GetSalaryRevisions getSalaryRevisions() {
            return (employeeId, limit) -> List.of(
                    aRevision("1200000.00", "1380000.00", Instant.parse("2026-09-11T09:15:30Z")),
                    aRevision("1000000.00", "1200000.00", Instant.parse("2025-04-01T09:15:30Z")));
        }

        private static SalaryRevisionView aRevision(String previous, String current, Instant at) {
            return new SalaryRevisionView(
                    Money.of(new BigDecimal(previous), INR),
                    Money.of(new BigDecimal(current), INR),
                    ChangeReason.MERIT,
                    new UserId(ACTOR),
                    at,
                    "Annual merit review");
        }
    }
}
