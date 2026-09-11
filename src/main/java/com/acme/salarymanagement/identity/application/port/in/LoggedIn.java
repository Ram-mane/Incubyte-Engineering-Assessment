package com.acme.salarymanagement.identity.application.port.in;

import java.time.Instant;

import com.acme.salarymanagement.identity.domain.Role;

/** What a successful login hands back. Never the hash, never the password. */
public record LoggedIn(String token, Instant expiresAt, Role role) {}
