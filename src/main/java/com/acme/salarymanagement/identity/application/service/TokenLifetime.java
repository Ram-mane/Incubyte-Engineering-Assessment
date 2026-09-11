package com.acme.salarymanagement.identity.application.service;

import java.time.Duration;

/** How long a token is good for, in one place so the issuer and the use case cannot disagree. */
public final class TokenLifetime {

    /** Thirty minutes, per docs/10-SECURITY.md. Short, because there is no revocation. */
    public static final Duration VALUE = Duration.ofMinutes(30);

    private TokenLifetime() {}
}
