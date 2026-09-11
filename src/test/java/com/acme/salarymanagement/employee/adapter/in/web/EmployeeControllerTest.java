package com.acme.salarymanagement.employee.adapter.in.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.acme.salarymanagement.employee.application.port.in.EmployeePage;
import com.acme.salarymanagement.employee.application.port.in.ListEmployees;
import com.acme.salarymanagement.employee.application.port.out.EmployeeSummary;
import com.acme.salarymanagement.shared.CountryCode;
import com.acme.salarymanagement.shared.CurrencyCode;
import com.acme.salarymanagement.shared.Department;
import com.acme.salarymanagement.shared.JobTitle;
import com.acme.salarymanagement.shared.Money;
import com.acme.salarymanagement.shared.SeniorityLevel;

/**
 * The JSON the directory screen receives. Stubbed at the inbound port rather than mocked, so the
 * assertions are about the document that goes over the wire and not about who called whom.
 */
@WebMvcTest(EmployeeController.class)
@Import(EmployeeControllerTest.OnePersonDirectory.class)
class EmployeeControllerTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void the_directory_returns_a_page_of_employees() throws Exception {
        mvc.perform(get("/api/v1/employees"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].familyName").value("Kapoor"))
                .andExpect(jsonPath("$.items[0].department").value("Engineering"))
                .andExpect(jsonPath("$.items[0].level").value("SENIOR"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(50))
                .andExpect(jsonPath("$.total").value(10000));
    }

    @Test
    void a_salary_crosses_the_wire_as_a_string_with_its_currency() throws Exception {
        mvc.perform(get("/api/v1/employees"))
                // A JSON number would be parsed as a double by every browser client, which is how
                // a salary quietly loses its last digits. The currency travels with the amount so
                // no client has to assume one.
                .andExpect(jsonPath("$.items[0].salary.amount").value("1200000.00"))
                .andExpect(jsonPath("$.items[0].salary.currency").value("INR"))
                .andExpect(jsonPath("$.items[0].salary.amount").isString());
    }

    @TestConfiguration
    static class OnePersonDirectory {

        @Bean
        ListEmployees listEmployees() {
            return (page, size) -> new EmployeePage(
                    List.of(new EmployeeSummary(
                            UUID.fromString("11111111-1111-1111-1111-111111111111"),
                            "E00042",
                            "Alice",
                            "Kapoor",
                            "alice.kapoor@acme.test",
                            new CountryCode("IN"),
                            new Department("Engineering"),
                            new JobTitle("Software Engineer"),
                            SeniorityLevel.SENIOR,
                            Money.of(new BigDecimal("1200000.00"), new CurrencyCode("INR")))),
                    page,
                    size,
                    10_000L);
        }
    }
}
