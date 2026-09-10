---
description: Day 2 — persistence, API, auth, seed, and the first three Angular screens (tasks 2.1-2.14)
---

Execute **PLAN.md tasks 2.1 through 2.14**, one commit per task. Stop after 2.14.

Goal for the day: **a real, deployed, usable product with 10,000 employees in it.** If everything
after today were abandoned, the submission would still be complete.

Follow the `hexagonal-module` skill for every use case — port in `application`, adapter implements
it, entity never leaves its package, `@PreAuthorize` on the use case not just the controller.

Order matters:
- **2.1–2.4 schema first.** `employee` with embedded salary; `salary_revision` with `INSERT`/`SELECT`
  grants only; a test proving the DB rejects `UPDATE` and `DELETE` on revisions as the app role;
  then the indexes from `docs/05-DATA-MODEL.md` §3 — each with a comment naming the query it serves.
- **2.5–2.7** adapters and use cases. Keyset pagination and trigram search, not `OFFSET`, not `LIKE`
  without the index.
- **2.8–2.9** controllers with RFC 7807 errors, then JWT + roles `HR_MANAGER` / `HR_ANALYST`.
- **2.10 the seed** — 10,000 employees across 6 countries, ~30,000 revisions, bands, FX. Fixed RNG
  seed, JDBC batch inserts, under 20 seconds, idempotent. Verify with `/seed`.
- **2.11–2.13** Angular: directory with filters and search, employee detail with revision log,
  change-salary dialog. Facades only — components never inject `HttpClient`.
- **2.14** redeploy and click through it yourself.

**Scope guard:** if you find yourself adding `effective_from`, validity intervals, as-of queries,
band enforcement or alerting, stop — all confirmed out of scope
(`docs/adr/0002-current-salary-with-audit-log.md`).

Finish with `/wrap`.
