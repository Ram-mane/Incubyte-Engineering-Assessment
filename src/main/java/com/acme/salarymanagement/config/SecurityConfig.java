package com.acme.salarymanagement.config;

import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

/**
 * Everything is closed unless it is named here.
 *
 * <p>A deny-by-default chain, so a new endpoint is protected by existing rather than by someone
 * remembering to protect it. Three things are open: logging in, which is where tokens come from;
 * the health probe, which the platform polls before the service is allowed to serve traffic; and
 * CORS preflight, which carries no credentials and must answer before the real request.
 *
 * <p>Stateless. No session is created or read, so any instance can serve any request and a token
 * is the whole of what a caller presents.
 */
@Configuration
@EnableMethodSecurity
class SecurityConfig {

    /** BCrypt at cost 12, per docs/10-SECURITY.md: slow on purpose. */
    private static final int BCRYPT_COST = 12;

    private static final int SHORTEST_USABLE_SECRET = 32;

    private final byte[] secret;

    SecurityConfig(@Value("${security.jwt.secret:}") String secret) {
        if (secret == null || secret.length() < SHORTEST_USABLE_SECRET) {
            // Refuse to start rather than sign payroll tokens with a guessable key. There is no
            // default here on purpose: a fallback secret in a repository is a published secret.
            throw new IllegalStateException(
                    "security.jwt.secret must be set and at least %d characters".formatted(SHORTEST_USABLE_SECRET));
        }
        this.secret = secret.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    @Bean
    SecurityFilterChain api(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(routes -> routes.requestMatchers(HttpMethod.POST, "/api/v1/auth/login")
                        .permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .oauth2ResourceServer(oauth2 ->
                        oauth2.jwt(jwt -> jwt.decoder(jwtDecoder()).jwtAuthenticationConverter(authorities())))
                .build();
    }

    /** The {@code role} claim becomes {@code ROLE_HR_MANAGER}, which is what hasRole asks for. */
    private static JwtAuthenticationConverter authorities() {
        JwtGrantedAuthoritiesConverter claims = new JwtGrantedAuthoritiesConverter();
        claims.setAuthoritiesClaimName("role");
        claims.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter((org.springframework.core.convert.converter.Converter<
                        org.springframework.security.oauth2.jwt.Jwt, java.util.Collection<GrantedAuthority>>)
                claims::convert);
        return converter;
    }

    @Bean
    JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withSecretKey(new SecretKeySpec(secret, "HmacSHA256"))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    @Bean
    JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(secret));
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(BCRYPT_COST);
    }
}
