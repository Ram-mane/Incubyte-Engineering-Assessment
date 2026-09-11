package com.acme.salarymanagement.identity.adapter.out.token;

import java.time.Instant;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import com.acme.salarymanagement.identity.application.port.out.TokenIssuer;
import com.acme.salarymanagement.identity.domain.Credentials;

/**
 * HS256, signed with the secret the resource server validates against.
 *
 * <p>The subject is the user's id rather than their email, because the id is what an audit record
 * stores: {@code salary_revision.changed_by} is a foreign key, and a token whose subject cannot be
 * that key would mean looking the user up again to write down who they were.
 */
@Component
class JwtTokenIssuer implements TokenIssuer {

    private final JwtEncoder encoder;

    JwtTokenIssuer(JwtEncoder encoder) {
        this.encoder = encoder;
    }

    @Override
    public String issueFor(Credentials user, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(user.userId().toString())
                .claim("email", user.email())
                .claim("role", user.role().name())
                .issuedAt(expiresAt.minus(com.acme.salarymanagement.identity.application.service.TokenLifetime.VALUE))
                .expiresAt(expiresAt)
                .build();
        return encoder.encode(JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
