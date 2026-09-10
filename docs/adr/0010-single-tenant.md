# ADR-0010: Single tenant, no speculative `tenant_id`

**Status:** Accepted · **Date:** 2026-09-09

## Context
The customer is one organisation. Multi-tenancy is a common speculative addition.

## Decision
Build single-tenant. Do not add an unused `tenant_id` column or a tenant resolver.

## Consequences
**Good.** Simpler schema, simpler queries, no partially-implemented isolation giving false confidence —
which is worse than none.

**Bad.** Adding tenancy later means a migration plus a filter on every query.

**How it would be done.** Discriminator-column tenancy with a Hibernate `@TenantId` and a
`TenantContext` resolved from the JWT, plus row-level security in PostgreSQL as a second line of
defence. Estimated at 2–3 days on the current schema — recorded here so the decision is informed
rather than accidental.
