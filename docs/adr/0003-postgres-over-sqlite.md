# ADR-0003: PostgreSQL 16, not SQLite

**Status:** Accepted · **Date:** 2026-09-09

## Context
The brief permits "a relational database of your choice, like SQLite".

## Decision
PostgreSQL 16 in all environments, including tests (via Testcontainers).

## Consequences
**Good.** `NUMERIC` for exact money, summed exactly. `percentile_cont` for the distribution charts —
computing medians in Java over 10,000 rows would be the exact anti-pattern this build is meant to
avoid. Covering and partial indexes for the directory. `pg_trgm` so `ILIKE '%name%'` search stays
indexed instead of degrading to a sequential scan. Per-table `GRANT`s, used to make the audit log
append-only at the database level. Real concurrency. Free managed tier on Neon.

**Bad.** Contributors need Docker; tests are slower than an in-memory database. Two extensions
(`pg_trgm`) to install in every environment.

## Rejected
- *SQLite* — no `percentile_cont`, no trigram indexing, no per-table grants, weak numeric typing and
  a single writer. Each of the four things this schema leans on to be both correct and fast is
  absent.
- *H2 for tests, Postgres for prod* — faster tests, but tests would then exercise a different engine
  than production, which is how dialect bugs reach users. Testcontainers with a reused container keeps
  the suite under 90 s.
