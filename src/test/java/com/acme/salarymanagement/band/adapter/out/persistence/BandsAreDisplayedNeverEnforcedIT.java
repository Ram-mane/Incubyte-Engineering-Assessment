package com.acme.salarymanagement.band.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;

import com.acme.salarymanagement.band.application.port.in.FindSalaryBand;
import com.acme.salarymanagement.band.domain.Position;
import com.acme.salarymanagement.employee.application.port.in.ChangeSalary;
import com.acme.salarymanagement.employee.application.port.in.ChangeSalaryCommand;
import com.acme.salarymanagement.employee.domain.ChangeReason;
import com.acme.salarymanagement.employee.domain.EmployeeId;
import com.acme.salarymanagement.employee.domain.UserId;
import com.acme.salarymanagement.shared.CountryCode;
import com.acme.salarymanagement.shared.CurrencyCode;
import com.acme.salarymanagement.shared.JobTitle;
import com.acme.salarymanagement.shared.Money;
import com.acme.salarymanagement.shared.SeniorityLevel;
import com.acme.salarymanagement.support.DatabaseOwner;
import com.acme.salarymanagement.support.PostgresIntegrationTest;

/**
 * The half of requirement 8 that is easy to pass by accident.
 *
 * <p>"Bands are displayed, not enforced" was confirmed with the customer. Now that a band is
 * actually on screen, nothing must have quietly started using it to refuse a change - and the only
 * way to know that is to put a salary far outside the band through the real use case and watch it
 * succeed. Before the display existed this test would have passed for the wrong reason; it exists
 * from the moment the reason could change.
 */
@WithMockUser(roles = "HR_MANAGER")
class BandsAreDisplayedNeverEnforcedIT extends PostgresIntegrationTest {

    private static final JobTitle ROLE = new JobTitle("ZzzenforceRole");
    private static final CurrencyCode USD = new CurrencyCode("USD");

    @Autowired
    private ChangeSalary changeSalary;

    @Autowired
    private FindSalaryBand bands;

    @Autowired
    private JdbcTemplate jdbc;

    private EmployeeId employee;
    private UserId actor;

    @BeforeEach
    void anEmployeeInsideATightBand() throws Exception {
        try (var owner = DatabaseOwner.connect();
                var insert = owner.prepareStatement(
                        """
                        INSERT INTO salary_band (job_title, seniority_level, country_code,
                                                 min_amount, mid_amount, max_amount, currency_code)
                        VALUES (?, 'MID', 'US', 80000.0000, 100000.0000, 120000.0000, 'USD')
                        ON CONFLICT DO NOTHING
                        """)) {
            insert.setString(1, ROLE.value());
            insert.executeUpdate();
        }

        UUID actorId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO app_user (id, email, password_hash, role) VALUES (?, ?, 'not-a-real-hash', 'HR_MANAGER')",
                actorId,
                actorId + "@acme.example");
        actor = new UserId(actorId);

        employee = new EmployeeId(UUID.randomUUID());
        jdbc.update(
                """
                INSERT INTO employee (id, employee_number, given_name, family_name, email, country_code,
                                      department, job_title, seniority_level, hire_date, status,
                                      salary_amount, salary_currency)
                VALUES (?, ?, 'Asha', 'Zzzenforce', ?, 'US', 'Zzzenforce', ?, 'MID',
                        DATE '2024-04-01', 'ACTIVE', 100000.00, 'USD')
                """,
                employee.value(),
                "BND-" + employee.value(),
                employee.value() + "@acme.example",
                ROLE.value());
    }

    @Test
    void a_pay_change_far_above_the_band_maximum_still_succeeds() {
        var wayOverTheBand = Money.of(new BigDecimal("5000000.00"), USD);

        var changed = changeSalary.change(
                new ChangeSalaryCommand(employee, wayOverTheBand, ChangeReason.MARKET_ADJUSTMENT, actor, "off-scale"));

        // Forty times the band maximum, accepted without a warning, a confirmation or a block.
        // The HR Manager is trusted to know what they are doing; the band tells them what they
        // are doing. Enforcement is future scope and this test is what keeps it future scope.
        assertThat(changed.salary().amount()).isEqualByComparingTo("5000000.00");
    }

    @Test
    void and_the_band_then_reports_that_they_are_above_it() {
        changeSalary.change(new ChangeSalaryCommand(
                employee, Money.of(new BigDecimal("5000000.00"), USD), ChangeReason.MARKET_ADJUSTMENT, actor, null));

        var band = bands.of(
                        ROLE, SeniorityLevel.MID, new CountryCode("US"), Money.of(new BigDecimal("5000000.00"), USD))
                .orElseThrow();

        // Reporting it is the whole feature: the change went through, and the record now says
        // plainly that this person sits above their approved range.
        assertThat(band.position()).isEqualTo(Position.ABOVE_MAX);
        assertThat(band.compaRatio()).isEqualByComparingTo("50.0000");
    }

    @org.junit.jupiter.api.AfterEach
    void removeWhatThisTestCommitted() throws Exception {
        try (var owner = DatabaseOwner.connect()) {
            for (String sql : new String[] {
                "DELETE FROM salary_revision WHERE employee_id = ?", "DELETE FROM employee WHERE id = ?",
            }) {
                try (var statement = owner.prepareStatement(sql)) {
                    statement.setObject(1, employee.value());
                    statement.executeUpdate();
                }
            }
            try (var statement = owner.prepareStatement("DELETE FROM app_user WHERE id = ?")) {
                statement.setObject(1, actor.value());
                statement.executeUpdate();
            }
            try (var statement = owner.prepareStatement("DELETE FROM salary_band WHERE job_title = ?")) {
                statement.setString(1, ROLE.value());
                statement.executeUpdate();
            }
        }
    }

    static {
        Locale.setDefault(Locale.ROOT);
    }
}
