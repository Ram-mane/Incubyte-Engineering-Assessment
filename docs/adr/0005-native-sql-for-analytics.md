# ADR-0005: Native SQL projections for analytics, JPA for writes

**Status:** Accepted · **Date:** 2026-09-09

## Context
The dashboard aggregates 10,000 employee rows across six currencies with grouping, percentiles and
FX conversion, and must recompute on every filter change. Writes are single-aggregate and benefit
from JPA's identity map and optimistic locking.

## Decision
A CQRS-lite split. The write side uses Spring Data JPA and domain aggregates. The `analytics` module
uses hand-written native SQL returning flat Java `record` projections, with no entities and no
persistence context.

## Consequences
**Good.** Aggregation runs where the data is. The four KPI cards are one round trip, not four. No
entity hydration, no dirty checking, no lazy-loading traps. The SQL is readable and can be
`EXPLAIN`ed directly — which is how the indexing evidence in `docs/perf/` is produced.

**Bad.** Two persistence idioms in one codebase; native SQL is not compile-time checked against the
schema. Mitigated by integration tests that run every analytics query against a real migrated
Testcontainers database, so a schema change breaks the build.

## Rejected
- *JPQL/Criteria for everything* — cannot express `percentile_cont` or window functions cleanly and
  tempts developers into loading entities to sum them in Java.
- *Full CQRS with a separate read store* — the read model would need syncing infrastructure to solve a
  problem one well-indexed database does not have. Listed as step 4 in the scalability path instead.
