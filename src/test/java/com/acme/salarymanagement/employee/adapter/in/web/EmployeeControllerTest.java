package com.acme.salarymanagement.employee.adapter.in.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.acme.salarymanagement.employee.application.port.in.DirectoryCursor;
import com.acme.salarymanagement.employee.application.port.in.DirectoryFilterOptions;
import com.acme.salarymanagement.employee.application.port.in.DirectoryRequest;
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
 * The document the directory screen receives, and the request it is read from. Stubbed at the
 * inbound port rather than mocked: the assertions are about the JSON and the parsed request, not
 * about who called whom.
 */
@WebMvcTest(EmployeeController.class)
@Import({EmployeeControllerTest.OnePersonDirectory.class, BadRequestHandler.class})
class EmployeeControllerTest {

    static final AtomicReference<DirectoryRequest> LAST_REQUEST = new AtomicReference<>();

    @Autowired
    private MockMvc mvc;

    @Test
    void the_directory_returns_items_a_cursor_and_a_total() throws Exception {
        mvc.perform(get("/api/v1/employees"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].familyName").value("Kapoor"))
                .andExpect(jsonPath("$.nextCursor").value("bmV4dA"))
                .andExpect(jsonPath("$.totalApprox").value(10000))
                // No page number and no page count: a keyset collection has a place to resume
                // from, not an index, and publishing one would invite deep links it cannot honour.
                .andExpect(jsonPath("$.page").doesNotExist())
                .andExpect(jsonPath("$.size").doesNotExist());
    }

    @Test
    void the_filter_options_are_what_the_directory_actually_contains() throws Exception {
        mvc.perform(get("/api/v1/employees/filter-options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.countries").isArray())
                .andExpect(jsonPath("$.departments[0]").value("Engineering"))
                .andExpect(jsonPath("$.levels[0]").value("JUNIOR"));
    }

    @Test
    void a_salary_crosses_the_wire_as_a_string_with_its_currency() throws Exception {
        mvc.perform(get("/api/v1/employees"))
                .andExpect(jsonPath("$.items[0].salary.amount").value("1200000.00"))
                .andExpect(jsonPath("$.items[0].salary.currency").value("INR"))
                .andExpect(jsonPath("$.items[0].salary.amount").isString());
    }

    @Test
    void every_filter_reaches_the_use_case_as_its_own_type() throws Exception {
        mvc.perform(get("/api/v1/employees")
                        .param("q", "rao")
                        .param("country", "DE")
                        .param("department", "Engineering")
                        .param("jobTitle", "Software Engineer")
                        .param("level", "SENIOR"))
                .andExpect(status().isOk());

        var request = LAST_REQUEST.get();
        assertFilters(request);
    }

    @Test
    void a_cursor_is_decoded_into_the_place_it_names() throws Exception {
        var cursor = new DirectoryCursor("Kapoor", "Alice", UUID.fromString("11111111-1111-1111-1111-111111111111"));

        mvc.perform(get("/api/v1/employees").param("cursor", cursor.encoded())).andExpect(status().isOk());

        org.assertj.core.api.Assertions.assertThat(LAST_REQUEST.get().after()).isEqualTo(cursor);
    }

    @Test
    void a_cursor_this_directory_did_not_issue_is_a_bad_request() throws Exception {
        mvc.perform(get("/api/v1/employees").param("cursor", "not-a-cursor"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("that is not a cursor this directory issued"));
    }

    @Test
    void a_country_that_does_not_exist_is_a_bad_request_rather_than_an_empty_page() throws Exception {
        mvc.perform(get("/api/v1/employees").param("country", "XX"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    private static void assertFilters(DirectoryRequest request) {
        org.assertj.core.api.Assertions.assertThat(request.search()).isEqualTo("rao");
        org.assertj.core.api.Assertions.assertThat(request.filters().country()).isEqualTo(new CountryCode("DE"));
        org.assertj.core.api.Assertions.assertThat(request.filters().department())
                .isEqualTo(new Department("Engineering"));
        org.assertj.core.api.Assertions.assertThat(request.filters().jobTitle())
                .isEqualTo(new JobTitle("Software Engineer"));
        org.assertj.core.api.Assertions.assertThat(request.filters().level()).isEqualTo(SeniorityLevel.SENIOR);
    }

    @TestConfiguration
    static class OnePersonDirectory {

        @Bean
        ListEmployees listEmployees() {
            return new ListEmployees() {

                @Override
                public DirectoryFilterOptions filterOptions() {
                    return new DirectoryFilterOptions(
                            List.of("DE", "IN"),
                            List.of("Engineering", "Finance"),
                            List.of("Accountant", "Software Engineer"),
                            List.of(SeniorityLevel.values()));
                }

                @Override
                public EmployeePage list(DirectoryRequest request) {
                    LAST_REQUEST.set(request);
                    return new EmployeePage(
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
                            "bmV4dA",
                            10_000L);
                }
            };
        }
    }
}
