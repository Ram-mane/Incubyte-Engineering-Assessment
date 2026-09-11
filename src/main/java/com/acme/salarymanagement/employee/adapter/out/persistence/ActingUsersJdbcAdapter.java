package com.acme.salarymanagement.employee.adapter.out.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.acme.salarymanagement.employee.application.port.out.ActingUsers;
import com.acme.salarymanagement.employee.domain.UserId;

/** Until authentication lands, this is what stops a revision being credited to nobody. */
@Repository
class ActingUsersJdbcAdapter implements ActingUsers {

    private final JdbcTemplate jdbc;

    ActingUsersJdbcAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean exists(UserId actor) {
        Boolean known = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM app_user WHERE id = ?)", Boolean.class, actor.value());
        return Boolean.TRUE.equals(known);
    }
}
