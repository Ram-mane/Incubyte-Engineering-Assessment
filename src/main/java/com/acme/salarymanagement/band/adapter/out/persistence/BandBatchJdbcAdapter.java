package com.acme.salarymanagement.band.adapter.out.persistence;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.acme.salarymanagement.band.application.port.out.BandWriteRepository;
import com.acme.salarymanagement.band.domain.SalaryBand;

/** Seed-only, over the privileged pool: at runtime the application may only read bands (D103). */
@Repository
@Profile("seed")
class BandBatchJdbcAdapter implements BandWriteRepository {

    private final JdbcTemplate jdbc;

    BandBatchJdbcAdapter(@Qualifier("seedJdbcTemplate") JdbcTemplate seedJdbcTemplate) {
        this.jdbc = seedJdbcTemplate;
    }

    @Override
    public void replaceAllWith(List<SalaryBand> bands) {
        jdbc.execute("TRUNCATE TABLE salary_band");
        jdbc.batchUpdate(
                """
                INSERT INTO salary_band (job_title, seniority_level, country_code,
                                         min_amount, mid_amount, max_amount, currency_code)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                new BatchPreparedStatementSetter() {

                    @Override
                    public void setValues(PreparedStatement statement, int index) throws SQLException {
                        SalaryBand band = bands.get(index);
                        statement.setString(1, band.jobTitle().value());
                        statement.setString(2, band.level().name());
                        statement.setString(3, band.country().code());
                        statement.setBigDecimal(4, band.min().amount());
                        statement.setBigDecimal(5, band.mid().amount());
                        statement.setBigDecimal(6, band.max().amount());
                        statement.setString(7, band.mid().currency().code());
                    }

                    @Override
                    public int getBatchSize() {
                        return bands.size();
                    }
                });
    }
}
