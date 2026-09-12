package com.acme.salarymanagement.band.adapter.out.persistence;

import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.acme.salarymanagement.band.application.port.out.SalaryBandRepository;
import com.acme.salarymanagement.band.domain.SalaryBand;
import com.acme.salarymanagement.shared.CountryCode;
import com.acme.salarymanagement.shared.CurrencyCode;
import com.acme.salarymanagement.shared.JobTitle;
import com.acme.salarymanagement.shared.Money;
import com.acme.salarymanagement.shared.SeniorityLevel;

/** One band, by its natural key, which is the table's primary key - so one index seek. */
@Repository
class SalaryBandJdbcAdapter implements SalaryBandRepository {

    private static final String BY_ROLE =
            """
            SELECT job_title, seniority_level, country_code, min_amount, mid_amount, max_amount, currency_code
            FROM   salary_band
            WHERE  job_title = ? AND seniority_level = ? AND country_code = ?
            """;

    private static final RowMapper<SalaryBand> AS_BAND = (row, number) -> {
        CurrencyCode currency = new CurrencyCode(row.getString("currency_code").trim());
        return new SalaryBand(
                new JobTitle(row.getString("job_title")),
                SeniorityLevel.valueOf(row.getString("seniority_level")),
                new CountryCode(row.getString("country_code").trim()),
                Money.of(row.getBigDecimal("min_amount"), currency),
                Money.of(row.getBigDecimal("mid_amount"), currency),
                Money.of(row.getBigDecimal("max_amount"), currency));
    };

    private final JdbcTemplate jdbc;

    SalaryBandJdbcAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<SalaryBand> forRole(JobTitle jobTitle, SeniorityLevel level, CountryCode country) {
        return jdbc.query(BY_ROLE, AS_BAND, jobTitle.value(), level.name(), country.code()).stream()
                .findFirst();
    }
}
