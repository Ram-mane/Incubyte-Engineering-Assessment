package com.acme.salarymanagement;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;

import com.acme.salarymanagement.support.PostgresIntegrationTest;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

/**
 * The filter chain, exercised as a caller meets it.
 *
 * <p>A security filter that fails open is indistinguishable from one that works: the tests pass,
 * the screen renders, and the endpoint is wide open. So these requests go through the real chain -
 * the application's own configuration, its own decoder, its own secret - rather than through a
 * slice with security stubbed out. The only thing that varies is the token.
 *
 * <p>Expiry is built from an instant, not from sleeping. A test that waits thirty minutes does not
 * get run, and a test that waits one second proves nothing about the thirty.
 */
@AutoConfigureMockMvc
class TheApiIsClosedWithoutAValidTokenIT extends PostgresIntegrationTest {

    private static final String DIRECTORY = "/api/v1/employees";
    private static final UUID SOMEBODY = UUID.fromString("00000000-0000-4000-8000-00000000ffff");

    @Autowired
    private MockMvc mvc;

    @Value("${security.jwt.secret}")
    private String realSecret;

    @Test
    void a_request_with_no_token_at_all_is_refused() throws Exception {
        mvc.perform(get(DIRECTORY)).andExpect(status().isUnauthorized());
    }

    @Test
    void an_unauthenticated_request_never_reaches_the_controller() throws Exception {
        // The assertion above is not enough on its own, and finding that out cost a deliberate
        // break: opening this route with permitAll left every other test green, because
        // @PreAuthorize on the use case still refused an anonymous caller and that refusal is
        // also a 401. Status alone cannot tell "the chain rejected it" from "the chain let it
        // through and the use case refused".
        //
        // limit=abc fails Spring's parameter binding, in the controller, before any use case is
        // consulted. So a closed chain answers 401 and an open one answers 400 - which is the
        // difference this test exists to see.
        mvc.perform(get(DIRECTORY).param("limit", "abc")).andExpect(status().isUnauthorized());
    }

    @Test
    void a_token_signed_with_the_wrong_key_is_refused() throws Exception {
        // Right shape, right claims, right expiry - and a signature this server did not make.
        String forged = tokenSignedWith(
                "an-attackers-key-of-entirely-sufficient-length", Instant.now().plusSeconds(600));

        mvc.perform(get(DIRECTORY).header(HttpHeaders.AUTHORIZATION, "Bearer " + forged))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void a_token_that_has_expired_is_refused() throws Exception {
        String stale = tokenSignedWith(
                realSecret, fixedAt("2026-09-11T09:00:00Z").instant().minusSeconds(60));

        mvc.perform(get(DIRECTORY).header(HttpHeaders.AUTHORIZATION, "Bearer " + stale))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void a_token_this_server_issued_and_still_honours_is_accepted() throws Exception {
        // The control. Without it the three refusals above would also pass against a chain that
        // rejects everything, including a correctly authenticated request.
        String valid = tokenSignedWith(realSecret, Instant.now().plusSeconds(600));

        mvc.perform(get(DIRECTORY).header(HttpHeaders.AUTHORIZATION, "Bearer " + valid))
                .andExpect(status().isOk());
    }

    @Test
    void logging_in_needs_no_token_because_it_is_where_tokens_come_from() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/auth/login")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@acme.example\",\"password\":\"wrong\"}"))
                // Unauthorized because the credentials are wrong, not because the route is closed:
                // a 403 or a redirect here would mean nobody could ever obtain a token.
                .andExpect(status().isUnauthorized());
    }

    @Test
    void the_health_probe_stays_open_or_the_platform_never_starts_us() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    private static Clock fixedAt(String instant) {
        return Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
    }

    /** A token with every claim the server wants, signed with whichever key is passed in. */
    private static String tokenSignedWith(String secret, Instant expiresAt) {
        var encoder = new NimbusJwtEncoder(new ImmutableSecret<>(
                new SecretKeySpec(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256")));
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(SOMEBODY.toString())
                .claims(all -> all.putAll(Map.of("email", "hr.manager@acme.example", "role", "HR_MANAGER")))
                .issuedAt(expiresAt.minusSeconds(1800))
                .expiresAt(expiresAt)
                .build();
        return encoder.encode(JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
