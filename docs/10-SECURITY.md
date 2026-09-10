# Security

Salary data is among the most sensitive data an HR system holds. This is a demo, but it is designed
as though it were not.

## Authentication
Spring Security with JWT (HS256, 30-minute expiry, secret from environment). `POST /auth/login`
issues; a stateless resource-server filter validates. No sessions, so any instance serves any request.
Passwords hashed with BCrypt, cost 12. Seeded demo users are in the README and clearly marked as demo.

## Authorisation
Two roles: `HR_MANAGER` (read/write) and `HR_ANALYST` (read-only).
Enforced with `@PreAuthorize` **at the use-case layer**, not only in controllers — so a future CLI,
scheduled job or second API cannot route around it. Controllers carry a matching annotation as
defence in depth. A test asserts that every public use-case method has an authorisation annotation,
so a new use case cannot ship unguarded.

## Data protection
- HTTPS everywhere (terminated at the platform edge).
- No salary figures in logs. A Logback masking converter redacts anything matching a money pattern.
- Errors are RFC 7807 with a correlation ID; stack traces and SQL never reach the client.
- The audit table records every write with actor, timestamp and before/after JSON.
- Secrets come from environment variables only. `.env` is git-ignored; a `.env.example` documents the keys.
- Gitleaks runs in CI.

## Input handling
- Bean Validation on every request DTO; unknown JSON fields rejected.
- All persistence uses parameterised queries — including the hand-written analytics SQL. No string
  concatenation of user input, and a Checkstyle rule forbids it.
- Uploads capped at 10 MB, content-type and extension checked, parsed as a stream.
- CORS restricted to the SPA origin; no wildcard.
- Security headers: HSTS, `X-Content-Type-Options`, `Referrer-Policy`, and a CSP without `unsafe-inline`.

## Supply chain
OWASP dependency-check in CI, failing on high severity. Dependabot enabled. Docker images built
`FROM eclipse-temurin:21-jre-alpine` as a non-root user, distroless-adjacent, no build tooling in the
runtime layer.

## Known gaps (deliberate, and stated rather than hidden)
- No refresh-token rotation or server-side revocation.
- No rate limiting or brute-force lockout on login.
- No field-level encryption at rest beyond what the managed database provides.
- No MFA.
- No PII retention or deletion policy — real GDPR compliance needs one.

Each would be required before this handled real employee data; none is required to demonstrate the
engineering. Listing them is part of the engineering.
