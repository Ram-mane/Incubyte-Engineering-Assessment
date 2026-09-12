package com.acme.salarymanagement.analytics.adapter.in.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import com.acme.salarymanagement.analytics.application.port.in.BreakdownDimension;
import com.acme.salarymanagement.analytics.application.port.in.DashboardFilters;
import com.acme.salarymanagement.analytics.application.port.in.DistributionDimension;
import com.acme.salarymanagement.analytics.application.port.in.GetPayrollBreakdown;
import com.acme.salarymanagement.analytics.application.port.in.GetPayrollSummary;
import com.acme.salarymanagement.analytics.application.port.in.GetSalaryDistribution;
import com.acme.salarymanagement.analytics.application.port.in.PayrollBreakdown;
import com.acme.salarymanagement.analytics.application.port.in.PayrollSummary;
import com.acme.salarymanagement.analytics.application.port.in.SalaryDistribution;
import com.acme.salarymanagement.shared.CurrencyCode;
import com.acme.salarymanagement.shared.Money;

/**
 * The documents the dashboard screen receives, and the requests they are read from.
 *
 * <p>Stubbed at the inbound port: the assertions are about the JSON and the parsed request, not
 * about who called whom.
 */
@WebMvcTest(DashboardController.class)
@WithMockUser(roles = "HR_ANALYST")
// ProblemHandler is not imported: it is package-private in the employee module's web adapter, and
// widening it so another module's test can name it would be the test dictating visibility. The
// slice picks up @RestControllerAdvice by scanning, which is also how it reaches this controller in
// production - though that it lives in `employee` at all is a smell worth its own decision.
@Import(DashboardControllerTest.StubbedAnalytics.class)
class DashboardControllerTest {

    static final AtomicReference<BreakdownDimension> LAST_DIMENSION = new AtomicReference<>();
    static final AtomicReference<DashboardFilters> LAST_FILTERS = new AtomicReference<>();
    static final AtomicReference<DistributionDimension> LAST_DISTRIBUTION = new AtomicReference<>();

    @Autowired
    private MockMvc mvc;

    @Test
    void a_breakdown_names_its_groups_and_their_spend() throws Exception {
        mvc.perform(get("/api/v1/dashboard/breakdown?groupBy=department"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupedBy").value("department"))
                .andExpect(jsonPath("$.groups.length()").value(2))
                .andExpect(jsonPath("$.groups[0].name").value("Engineering"))
                .andExpect(jsonPath("$.groups[0].headcount").value(3))
                // A string, not a JSON number: a large payroll figure loses its last digits as a
                // double in a browser.
                .andExpect(jsonPath("$.groups[0].totalSpend.amount").value("220400.00"))
                .andExpect(jsonPath("$.groups[0].totalSpend.currency").value("USD"))
                .andExpect(jsonPath("$.reportingCurrency").value("USD"))
                .andExpect(jsonPath("$.ratesAsOf").value("2026-09-01"));
    }

    @Test
    void group_by_is_read_in_the_case_the_api_documents() throws Exception {
        mvc.perform(get("/api/v1/dashboard/breakdown?groupBy=country")).andExpect(status().isOk());

        // 04-API-DESIGN documents groupBy=department|country|level in lower case. Spring's default
        // enum binding is case-sensitive, so without a converter the documented request 400s.
        org.assertj.core.api.Assertions.assertThat(LAST_DIMENSION.get()).isEqualTo(BreakdownDimension.COUNTRY);
    }

    @Test
    void an_unknown_dimension_is_refused_rather_than_reaching_the_query() throws Exception {
        mvc.perform(get("/api/v1/dashboard/breakdown?groupBy=salary_amount;DROP TABLE employee"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void a_breakdown_without_a_dimension_is_refused() throws Exception {
        mvc.perform(get("/api/v1/dashboard/breakdown")).andExpect(status().isBadRequest());
    }

    @Test
    void the_filters_reach_the_use_case_as_values_not_strings() throws Exception {
        mvc.perform(get("/api/v1/dashboard/breakdown?groupBy=level&country=DE&department=Engineering&currency=eur"))
                .andExpect(status().isOk());

        DashboardFilters filters = LAST_FILTERS.get();
        org.assertj.core.api.Assertions.assertThat(filters.country().code()).isEqualTo("DE");
        org.assertj.core.api.Assertions.assertThat(filters.department().value()).isEqualTo("Engineering");
        org.assertj.core.api.Assertions.assertThat(filters.reportingCurrency().code())
                .as("a currency is upper-cased at the edge, so ?currency=eur is not a different currency")
                .isEqualTo("EUR");
    }

    @Test
    void a_distribution_names_its_quartiles_and_its_range() throws Exception {
        mvc.perform(get("/api/v1/dashboard/distribution?groupBy=jobTitle"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupedBy").value("jobTitle"))
                .andExpect(jsonPath("$.groups[0].name").value("Software Engineer"))
                .andExpect(jsonPath("$.groups[0].headcount").value(4))
                .andExpect(jsonPath("$.groups[0].lowest.amount").value("100000.00"))
                .andExpect(jsonPath("$.groups[0].p25.amount").value("100000.00"))
                .andExpect(jsonPath("$.groups[0].median.amount").value("200000.00"))
                .andExpect(jsonPath("$.groups[0].p75.amount").value("300000.00"))
                .andExpect(jsonPath("$.groups[0].p90.amount").value("400000.00"))
                .andExpect(jsonPath("$.groups[0].highest.amount").value("400000.00"));
    }

    @Test
    void a_camel_case_dimension_reaches_the_use_case_as_its_enum() throws Exception {
        mvc.perform(get("/api/v1/dashboard/distribution?groupBy=jobTitle")).andExpect(status().isOk());

        // The API spells it jobTitle; the enum is JOB_TITLE. Upper-casing alone gives JOBTITLE,
        // which does not exist - so the conversion has to insert the underscore.
        org.assertj.core.api.Assertions.assertThat(LAST_DISTRIBUTION.get()).isEqualTo(DistributionDimension.JOB_TITLE);
    }

    @Test
    void a_dimension_the_distribution_has_no_meaning_for_is_refused() throws Exception {
        // `country` is a breakdown dimension, not a distribution one: a country is not a peer
        // group. Sharing one enum between the endpoints would have made this a 200.
        mvc.perform(get("/api/v1/dashboard/distribution?groupBy=country")).andExpect(status().isBadRequest());
    }

    @TestConfiguration
    static class StubbedAnalytics {

        @Bean
        GetPayrollBreakdown breakdowns() {
            return (dimension, filters) -> {
                LAST_DIMENSION.set(dimension);
                LAST_FILTERS.set(filters);
                CurrencyCode currency = filters.reportingCurrency();
                return new PayrollBreakdown(
                        dimension,
                        List.of(
                                new PayrollBreakdown.BreakdownRow(
                                        "Engineering", 3, money("220400.00", currency), money("73466.67", currency)),
                                new PayrollBreakdown.BreakdownRow(
                                        "Finance", 2, money("140000.00", currency), money("70000.00", currency))),
                        LocalDate.of(2026, 9, 1));
            };
        }

        @Bean
        GetSalaryDistribution distributions() {
            return (dimension, filters) -> {
                LAST_DISTRIBUTION.set(dimension);
                CurrencyCode currency = filters.reportingCurrency();
                return new SalaryDistribution(
                        dimension,
                        List.of(new SalaryDistribution.DistributionRow(
                                "Software Engineer",
                                4,
                                money("100000.00", currency),
                                money("100000.00", currency),
                                money("200000.00", currency),
                                money("300000.00", currency),
                                money("400000.00", currency),
                                money("400000.00", currency))),
                        LocalDate.of(2026, 9, 1));
            };
        }

        @Bean
        GetPayrollSummary summaries() {
            return filters -> new PayrollSummary(
                    1,
                    money("100000.00", filters.reportingCurrency()),
                    money("100000.00", filters.reportingCurrency()),
                    money("100000.00", filters.reportingCurrency()),
                    LocalDate.of(2026, 9, 1));
        }

        private static Money money(String amount, CurrencyCode currency) {
            return Money.of(new BigDecimal(amount), currency);
        }
    }
}
