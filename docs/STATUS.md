# Session status

> Written at the end of each session by `/wrap`, read at the start of each by `/prime`.
> Hand-written, so `git log` wins any disagreement.

**Last updated:** 2026-09-12, end of the Day 2 session and the start of Day 3

## Position

**Day 2 is complete. Day 3 has started: the KPI query is built and measured, the dashboard screen
is not.** `PLAN.md` 2.1–2.14 are done, including 2.10 in full. 2.15 is still blocked. Of Day 3,
3.2's query and endpoint are done; 3.1 was absorbed into V5.

There is a working, deployed, authenticated product: sign in at
https://salary-management-ui-5bp6.onrender.com as `hr.manager@acme.example` / `demo-password`,
browse 10,000 employees, open one, change their pay, watch the audit log record it.
`GET /api/v1/dashboard/summary` returns the four KPI cards from a single query, but **nothing
renders them yet.**

176 unit tests + 85 integration tests + 29 Angular specs, all green. 11 ArchUnit rules. CI green on
every push. `main` is in sync with `origin/main` at `01e34a0`.

Unit tests dropped 186 → 176 deliberately: exactly the ten `Money.convertTo` tests, deleted with
the method under D136. Nothing else went with them.

## Landed today

Twenty-six commits, `5dfe3b9` through `01e34a0`. The Day 2 build is listed in the previous status
file; this session added:

- `docs: update session status` — the Day 2 handoff
- `docs: record decision on jdbctemplate everywhere and jpa on a read path` — **ADR-0014**
- `docs: record eleven decisions from day two` — D121–D131, and D120 marked withdrawn
- `feat: seed salary bands, exchange rates and thirty thousand revisions` — finishes 2.10
- `feat: add the payroll summary in one round trip` — **3.2**, plus D135–D137 and the D039 deletion

## Uncommitted

_(none — working tree clean)_

## Blockers

**1. ~~The dashboard has no screen.~~ Cleared.** PLAN 3.9 landed: four KPI cards and the four
filters, one round trip per filter change, 23 new Angular specs. The breakdown and distribution
(3.3, 3.4) are still to come.

**2. Two concurrent pay changes both succeed.** There is no optimistic locking on the employee
`UPDATE`, so two managers changing the same salary at once each write a revision, both carrying the
same `previous_amount`, and the employee ends on whichever committed last. The audit log then
describes a history that never happened. Fix is `WHERE current_salary = ?` on the `UPDATE` and a
rejection when zero rows change — no ORM, no version column, about twenty minutes. Recorded in
ADR-0014's consequences. **Do it after the dashboard renders, not before.**

**3. Render free tier cold start exceeds two minutes.** The first request after idle returned
nothing for 120 s; the next returned in 1.3 s. Warm the API with
`curl https://salary-management-api-bv03.onrender.com/actuator/health` **before demoing or
recording anything**, or the first two minutes of the video are a spinner.

**4. Credentials shared in a chat transcript still need rotating.** The Render API key and the Neon
database password. Neither is in the repository, but both are in a log.

**5. `PLAN.md` 2.15 is deliberately unmet, not blocked** (**D139**). It needs all five module
packages to exist; `compensation` and `bulkimport` do not, and `compensation` may never — D111
records why the change-salary use case lives in `employee`. Empty packages will not be created to
make the gate pass. Revisit when `bulkimport` lands at 3.10.

**6. ~~The mutation gate has not run since Day 1.~~ Cleared.** Re-run over the changed domain:
**178 of 201 killed, 89%**, test strength 96%, against a 70% threshold. The README quoted the
threshold as though it were the score and now quotes the measurement (**D138**).

**7. Domain rejections still return 400 where the API doc says 422/409** (D112).

## Decisions recorded

**D090–D137**, forty-eight rows, plus **ADR-0014**. This session added D120 (withdrawn, pointing at
the ADR), D121–D131 from the sweep, and D132–D137 from the seed and the KPI query.

Load-bearing for tomorrow:

- **ADR-0014** — every adapter is `JdbcTemplate`; JPA is demonstrated on a **read** path after the
  dashboard ships. The reason is not preference: dirty checking is an ambient write path, and a
  managed `Employee` would flush a salary change with no revision, below every guard in the system.
  It supersedes the write half of ADR-0005; `03-ARCHITECTURE` §7 was wrong and is corrected.
- **D135** — money percentiles use `percentile_disc`, never `percentile_cont`. Applies to 3.4.
- **D137** — per-row FX conversion in SQL is exact; ADR-0013 still holds and the drift is measured
  at zero.
- **D132/D133** — FX rates are seeded by the migration; revisions are produced by `changeSalaryTo`.
- **D103** — the seed runs on its own pool; `salary_band` and `exchange_rate` are `SELECT`-only at
  runtime.

**No rows remain on probation.** D039 was settled by deleting `Money.convertTo` (D136).

## Next action

`PLAN.md` tasks 3.3 and 3.4 — the breakdown by dimension and the distribution by role.

3.9 (the dashboard screen) is done. The previous status file called it "task 3.6"; 3.6 is the
shared filter set across the dashboard endpoints and 3.9 is the screen — the number was wrong, the
work described was right.

Then the optimistic-locking fix, then the JPA read-path slice, then Playwright, README, k6 and the
video.

**The video gets 45 minutes tomorrow morning, with the API warmed first.**

## Gotchas

- **Builds need `JAVA_HOME=$HOME/.jdks/jdk-21`.** Node 22 is at `~/.nodejs/current/bin`.
- **Spotless deletes an import the moment nothing uses it.** Add an import and its first use in the
  same edit, and compile before formatting — this cost three cycles on one ArchUnit rule.
- **PMD's `GuardLogStatement` fires on any log argument that is a method call.** Hoist it into a
  local first. It has now failed the build three times, always on a seed runner's log line.
- **`mvn clean` after deleting anything under `src/main/resources`.** Maven does not prune
  `target/classes`.
- **A bare `? IS NULL` is rejected by PostgreSQL.** Optional filters need `CAST(:p AS text) IS NULL`.
- **A `@SpringBootTest` on a subclass replaces the parent's `properties`, it does not merge.**
- **The Testcontainers database is shared and the app role has no `DELETE`.** No test may assume an
  empty table, and no test may clean up by deleting employees — assert relative to a baseline, or
  pin assertions to a value nothing else generates.
- **Any test that calls a use case needs `@WithMockUser`**, because authorisation is at the use-case
  layer. Four tests have failed this way; the error is
  `AuthenticationCredentialsNotFoundException`.
- **Applying a migration by hand and then letting Flyway run it fails.** Delete the objects *and* the
  `flyway_schema_history` row first.
- **`ng serve` does not pick up new files in `public/` without a restart**, and Angular templates
  read `@` as control flow — an email address in template text must be `&#64;`.
- **Render auto-deploys on every push regardless of CI**, ~3.5 min for the API, ~1.5 for the site.
- **Never point the datasource at Neon's `-pooler` host** (D110). `SET ROLE` leaks between clients
  and Flyway fails with `permission denied for table flyway_schema_history`.
- **Capture a "before" query plan with `BEGIN; DROP INDEX …; EXPLAIN …; ROLLBACK;`** — truthful, and
  nothing is rebuilt.
