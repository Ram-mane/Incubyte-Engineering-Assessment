---
description: Scaffold a use case in the correct hexagonal order, test-first
---

Create the use case: **$ARGUMENTS**

Follow the `hexagonal-module` skill exactly, and follow `tdd-cycle` for each step. Stop after each
numbered step and show me what you produced before continuing.

1. Restate the behaviour in one sentence and name the module it belongs to. If it needs a domain rule
   that does not exist yet, say so before writing anything.
2. Inbound port + command/view records in `application/port/in/`.
3. Any new outbound port in `application/port/out/`, expressed in domain language.
4. Domain changes, with unit tests first. Rules go in domain objects, not in the service.
5. Service test against an in-memory fake port.
6. Service implementation, package-private, with `@PreAuthorize`.
7. Persistence adapter + Flyway migration if needed, with indexes and a comment naming the query each serves.
8. Controller + DTOs, RFC 7807 errors, `openapi.yaml` update.
9. Integration test through HTTP against Testcontainers, asserting outcome and statement count.
10. Run `mvn verify`, then `/review`.

Do not skip to the controller. Do not put business rules in the service. Do not let a JPA entity
escape its package.
