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
import org.springframework.transaction.annotation.Transactional;

import com.acme.salarymanagement.band.application.port.in.BandForSalary;
import com.acme.salarymanagement.band.application.port.in.FindSalaryBand;
import com.acme.salarymanagement.band.domain.Position;
import com.acme.salarymanagement.shared.CountryCode;
import com.acme.salarymanagement.shared.CurrencyCode;
import com.acme.salarymanagement.shared.JobTitle;
import com.acme.salarymanagement.shared.Money;
import com.acme.salarymanagement.shared.SeniorityLevel;
import com.acme.salarymanagement.support.DatabaseOwner;
import com.acme.salarymanagement.support.PostgresIntegrationTest;

/**
 * Reading the band that applies to somebody, and where their pay sits in it.
 *
 * <p>The band exists to be <em>shown</em>. Requirement 8 asks for it on the employee record, and
 * until now it passed only because nothing displayed a band at all - an absence is not a feature.
 * {@link #a_salary_far_above_the_band_is_reported_not_refused} is the other half: displayed and
 * never enforced are both requirements, and the second one needs a test or it is an accident.
 */
@Transactional
@WithMockUser(roles = "HR_ANALYST")
class SalaryBandLookupIT extends PostgresIntegrationTest {

    private static final JobTitle ROLE = new JobTitle("ZzzbandRole");
    private static final CountryCode US = new CountryCode("US");
    private static final CurrencyCode USD = new CurrencyCode("USD");

    @Autowired
    private FindSalaryBand bands;

    @Autowired
    private JdbcTemplate jdbc;

    /** 80,000 / 100,000 / 120,000 USD, so every classification is one line of arithmetic. */
    @BeforeEach
    void aBandForOneRole() throws Exception {
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
    }

    @Test
    void a_band_is_found_by_role_level_and_market() {
        BandForSalary band =
                bands.of(ROLE, SeniorityLevel.MID, US, usd("100000.00")).orElseThrow();

        assertThat(band.min().amount()).isEqualByComparingTo("80000.00");
        assertThat(band.mid().amount()).isEqualByComparingTo("100000.00");
        assertThat(band.max().amount()).isEqualByComparingTo("120000.00");
    }

    @Test
    void a_salary_at_the_midpoint_is_a_compa_ratio_of_one() {
        BandForSalary band =
                bands.of(ROLE, SeniorityLevel.MID, US, usd("100000.00")).orElseThrow();

        assertThat(band.compaRatio()).isEqualByComparingTo("1.0000");
        assertThat(band.position()).isEqualTo(Position.WITHIN);
    }

    @Test
    void a_salary_under_the_minimum_is_reported_as_below_min() {
        BandForSalary band =
                bands.of(ROLE, SeniorityLevel.MID, US, usd("70000.00")).orElseThrow();

        assertThat(band.position()).isEqualTo(Position.BELOW_MIN);
        assertThat(band.compaRatio()).isEqualByComparingTo("0.7000");
    }

    @Test
    void a_salary_over_the_maximum_is_reported_as_above_max() {
        BandForSalary band =
                bands.of(ROLE, SeniorityLevel.MID, US, usd("150000.00")).orElseThrow();

        assertThat(band.position()).isEqualTo(Position.ABOVE_MAX);
    }

    @Test
    void a_role_with_no_band_is_absent_rather_than_a_band_of_zero() {
        var none = bands.of(new JobTitle("ZzzNoSuchRole"), SeniorityLevel.LEAD, US, usd("100000.00"));

        // The screen says "no band defined for this role and level". A zero band would put an
        // employee 'above max' against a range nobody approved.
        assertThat(none).isEmpty();
    }

    @Test
    void a_band_in_a_different_market_does_not_answer_for_this_one() {
        var germany = bands.of(ROLE, SeniorityLevel.MID, new CountryCode("DE"), usd("100000.00"));

        // Bands are per market. Falling back to another country's range would compare a German
        // salary against American money.
        assertThat(germany).isEmpty();
    }

    @Test
    void the_band_is_read_in_one_statement() {
        long statements = com.acme.salarymanagement.support.CountingDataSource.statementsIssuedBy(
                () -> bands.of(ROLE, SeniorityLevel.MID, US, usd("100000.00")));

        assertThat(statements).isEqualTo(1);
    }

    private static Money usd(String amount) {
        return Money.of(new BigDecimal(amount), USD);
    }

    static {
        Locale.setDefault(Locale.ROOT);
    }

    /** Bands are seeded, never written by the application: D103 grants it SELECT only. */
    @org.junit.jupiter.api.AfterEach
    void removeTheBandThisTestAdded() throws Exception {
        try (var owner = DatabaseOwner.connect();
                var delete = owner.prepareStatement("DELETE FROM salary_band WHERE job_title = ?")) {
            delete.setString(1, ROLE.value());
            delete.executeUpdate();
        }
    }

    static final UUID UNUSED = UUID.randomUUID();
}
