package com.acme.salarymanagement.identity.adapter.in.web;

import java.time.Instant;

import com.acme.salarymanagement.identity.application.port.in.LoggedIn;

record LoginResponse(String token, Instant expiresAt, String role) {

    static LoginResponse of(LoggedIn session) {
        return new LoginResponse(
                session.token(), session.expiresAt(), session.role().name());
    }
}
