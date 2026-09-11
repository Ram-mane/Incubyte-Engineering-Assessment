package com.acme.salarymanagement.identity.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.acme.salarymanagement.identity.application.port.out.UserCredentials;
import com.acme.salarymanagement.identity.domain.Credentials;
import com.acme.salarymanagement.identity.domain.Role;

@Repository
class UserCredentialsJdbcAdapter implements UserCredentials {

    private static final RowMapper<Credentials> AS_CREDENTIALS = (row, number) -> new Credentials(
            row.getObject("id", UUID.class),
            row.getString("email"),
            row.getString("password_hash"),
            Role.valueOf(row.getString("role")));

    private final JdbcTemplate jdbc;

    UserCredentialsJdbcAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Credentials> forEmail(String email) {
        return jdbc
                .query(
                        "SELECT id, email, password_hash, role FROM app_user WHERE lower(email) = lower(?)",
                        AS_CREDENTIALS,
                        email)
                .stream()
                .findFirst();
    }
}
