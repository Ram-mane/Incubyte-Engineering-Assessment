package com.acme.salarymanagement.identity.application.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.acme.salarymanagement.identity.application.port.in.LogIn;
import com.acme.salarymanagement.identity.application.port.in.LoggedIn;
import com.acme.salarymanagement.identity.application.port.out.TokenIssuer;
import com.acme.salarymanagement.identity.application.port.out.UserCredentials;
import com.acme.salarymanagement.identity.domain.Credentials;

/**
 * Checks a password and mints a short-lived token.
 *
 * <p>The expiry comes from the injected clock, like every other instant in this system: a test can
 * ask what a token issued an hour ago does without waiting an hour, and the token's lifetime is a
 * property of the configuration rather than of when the JVM started.
 */
@Service
class LogInService implements LogIn {

    private final UserCredentials users;
    private final TokenIssuer tokens;
    private final PasswordEncoder passwords;
    private final Clock clock;

    LogInService(UserCredentials users, TokenIssuer tokens, PasswordEncoder passwords, Clock clock) {
        this.users = users;
        this.tokens = tokens;
        this.passwords = passwords;
        this.clock = clock;
    }

    @Override
    public LoggedIn withPassword(String email, String password) {
        Credentials user = users.forEmail(email).orElseThrow(BadCredentials::new);
        if (!passwords.matches(password, user.passwordHash())) {
            throw new BadCredentials();
        }
        Instant expiresAt = clock.instant().plus(TokenLifetime.VALUE);
        return new LoggedIn(tokens.issueFor(user, expiresAt), expiresAt, user.role());
    }
}
