package com.acme.salarymanagement.identity.application.port.out;

import java.time.Instant;

import com.acme.salarymanagement.identity.domain.Credentials;

/** Minting the bearer token. Signing is an adapter's concern, not the use case's. */
public interface TokenIssuer {

    String issueFor(Credentials user, Instant expiresAt);
}
