package com.acme.salarymanagement.identity.domain;

import java.util.Objects;
import java.util.UUID;

/** A user as authentication needs them: who they are, what they may do, and the hash to check. */
public record Credentials(UUID userId, String email, String passwordHash, Role role) {

    public Credentials {
        Objects.requireNonNull(userId, "a user id is required");
        Objects.requireNonNull(email, "an email is required");
        Objects.requireNonNull(passwordHash, "a password hash is required");
        Objects.requireNonNull(role, "a role is required");
    }
}
