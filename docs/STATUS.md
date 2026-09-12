# Session status

> Written at the end of each session by `/wrap`, read at the start of each by `/prime`.
> Hand-written, so `git log` wins any disagreement.

**Last updated:** 2026-09-11, end of Day 2

## Position

**Day 2 is functionally complete and deployed, with two named gaps.** Tasks 2.1–2.9, 2.11–2.14
are done. `PLAN.md` 2.10 is **partial** and 2.15 is **not started** — see Blockers.

There is a working, deployed, authenticated product with 10,000 employees in it. Sign in at
https://salary-management-ui-5bp6.onrender.com as `hr.manager@acme.example` / `demo-password`,
browse the directory, open a person, change their pay, watch the audit log record it.

184 unit tests + 76 integration tests + 29 Angular specs, all green. 11 ArchUnit rules. CI green
on every push today.

## Landed today

Twenty commits, `5dfe3b9` through `d67c54f` (the previous session ended at `599652b`):

- `feat: add employee schema` — V2: app_user, employee, application-assigned uuids, no defaults
- `feat: add append-only salary revision log` — V3, the `salary_app` role, insert-only grants
- `fix: trace revision check constraints to the invariants they mirror`
- `fix: make the application connect as the restricted role`
- `test: verify salary revisions are append-only at the database`
- `fix: enforce the positive-salary invariant in the aggregate`
- `feat: grant each table in the migration that creates it`
- `docs: correct an overstated claim about what caught the pool misconfiguration`
- `docs: record the seed privilege ruling and two gate findings`
- `feat: list employees through a paginated directory endpoint` — the first vertical slice
- `feat: seed ten thousand employees over a privileged pool` — 10,000 in 1,418 ms
- `feat: add employee directory screen`
- `fix: make the directory test independent of what else is in the database`
- `test: count statements for real, and make CLAUDE.md true again`
- `feat: page the directory by cursor, with filters and search` — keyset, openapi.yaml born
- `fix: refuse to start when a pool has the wrong database identity`
- `feat: change salary and read the audit log through the api`
- `feat: add jwt authentication and role-based authorisation`
- `perf: add directory and dashboard indexes` — V4 plus `docs/evidence/`
- `feat: add login, employee detail and the change-salary dialog`

## Uncommitted

_(none — working tree clean, `main` in sync with `origin/main`)_

## Blockers

**1. Render free tier cold start exceeds two minutes.** The first request after the service has
been idle returned nothing at all — `curl` gave up at 120 s with HTTP 000. The request immediately
after it returned 200 in 1.3 s. So the deployment is not down, but anyone opening the demo link
cold sees a hang, and a demo video recorded without warming it up first will show one. Warm the API
with `curl .../actuator/health` before demoing or recording. This is the free plan's spin-down; the
only real fixes are a paid plan or a keep-alive ping.

**2. Credentials were pasted into a chat transcript and must be rotated.** The Render API key and
the Neon database password were both shared in conversation today to fix the deploy. Rotate both:
Render → Account Settings → API Keys; Neon → reset the role password, then update
`SPRING_DATASOURCE_PASSWORD` on the API service. Neither is in the repository.

**3. `PLAN.md` 2.10 is partial.** The seed writes 10,000 employees and the two demo users. It does
**not** write the ~30,000 salary revisions, the ~180 salary bands, or the FX table — and the FX
table and `salary_band` table do not exist yet at all. Day 3's dashboard normalises six currencies
through the FX table, so **3.2 cannot be built until those tables and their seed data exist.** That
is the first thing that will block tomorrow.

**4. `PLAN.md` 2.15 cannot be done yet.** It sets `failOnEmptyShould=true` "now that all five module
packages exist". Three exist (`employee`, `band`, `identity`); `analytics`, `compensation` and
`bulkimport` do not. `compensation` may never exist — D111 records why the change-salary use case
lives in `employee` instead. Revisit 2.15 once `analytics` lands with 3.2.

**5. The mutation gate has not run since Day 1.** `mvn -Pmutation test` was not run today, and the
domain gained `Department` and `Employee.requirePositive` since. The 70% threshold is unverified
against today's code.

**6. Domain rejections return 400, not the documented 422/409.** Recorded as D112. Needs typed
domain rejections rather than message-matching; belongs with the RFC 7807 work.

## Decisions recorded

Thirty rows, **D090–D119**. The ones that will matter tomorrow:

- **D091** — no column in the schema has a `DEFAULT`: not for identity, not for time.
- **D092** — `Department` is an inline natural key, not a table. There is no department table.
- **D094** — `salary_revision.id` is a uuid the adapter assigns, not a `BIGSERIAL`. Supersedes D079.
- **D097 / D110** — the application connects as `salary_app` via `SET ROLE`, which is why the
  datasource **must** be Neon's direct endpoint and never the `-pooler` one.
- **D103** — the seed runs on its own unrestricted pool; `exchange_rate` and `salary_band` get
  `SELECT` only at runtime. Build tomorrow's seeding accordingly.
- **D108 / D109** — keyset paging satisfies ADR-0004; `totalApprox` is an exact count on the first
  page only and absent thereafter.
- **D111** — the change-salary use case lives in `employee`, not `compensation`.
- **D117** — `ix_employee_filter` was specified, measured, and not shipped.
- **D118** — the three shapes of vacuous gate, and the rule that a suite of refusals needs a
  control that succeeds.

`Money.convertTo` is still on **probation (D039)**: the condition is "delete if it has no
production caller by end of Day 3", and it still has none. 3.2 normalises in SQL (ADR-0013), so it
probably still will not. **Settle it tomorrow rather than letting the deadline pass silently.**

## Next action

`PLAN.md` task 3.1 — "Flyway V4: `salary_band`, `exchange_rate`, seeded FX rates", except that the
migration is now **V5** (V4 is the index migration that landed today). Then 3.2, the four-KPI
single-round-trip query, which is the primary deliverable and a deep-review task.

Do 2.10's remaining half first — bands, FX and revisions in the seed — or 3.2 has no data to
aggregate.

## Gotchas

- **Builds need `JAVA_HOME=$HOME/.jdks/jdk-21`.** Node 22 is at `~/.nodejs/current/bin`. Neither is
  on the default `PATH`.
- **Spotless deletes an import the moment nothing uses it.** Adding an import in one edit and the
  code that uses it in the next means `spotless:apply` removes the import in between. Cost three
  cycles on one ArchUnit rule today. Add both in the same edit, and compile before formatting.
- **`mvn clean` after deleting anything under `src/main/resources`.** Maven does not prune
  `target/classes`, so a deleted migration kept running and reddened a green build.
- **A bare `? IS NULL` is rejected by PostgreSQL** — "could not determine data type of parameter".
  Every optional filter needs `CAST(:param AS text) IS NULL`.
- **A `@SpringBootTest` on a subclass replaces the parent's `properties`, it does not merge.**
  `EmployeeSeedIT` lost the JWT secret that way and failed to load its context.
- **The Testcontainers database is shared by the whole suite and the app role cannot delete.** No
  test may assume an empty table; assert relative to a baseline, or clean up with the seed pool.
- **`ng serve` does not pick up new files in `public/` without a restart.**
- **Angular templates read `@` as control flow.** An email address in template text must be
  `&#64;`, or the build fails with a confusing parse error.
- **Applying a migration by hand and then letting Flyway run it fails.** If you `psql` a migration
  into the local database, delete the objects *and* the `flyway_schema_history` row before starting
  the app.
- **PMD fires on things Checkstyle does not**: literals in `if`, unguarded log statements,
  `ResultSet.next()` without checking its return. It is the gate most likely to fail a commit.
- **Render deploys take ~3.5 minutes for the API, ~1.5 for the static site**, and auto-deploy fires
  on every push regardless of CI.
- **Evidence method worth reusing**: capture a "before" plan with
  `BEGIN; DROP INDEX …; EXPLAIN (ANALYZE, BUFFERS) …; ROLLBACK;` — truthful, and nothing is rebuilt.
