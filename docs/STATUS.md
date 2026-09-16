# Session status

> Written at the end of each session by `/wrap`, read at the start of each by `/prime`.
> Hand-written, so `git log` wins any disagreement.

**Last updated:** 2026-09-12, end of Day 3. Submission day.

## Position

**Day 3, tasks 3.3, 3.4, 3.9 and the band work are complete. The build is feature-complete for
submission and verified against the deployed instance.** The demo video is recorded and linked in
the README (3.21, 3.19). PLAN's remaining Day 3 items are 3.11–3.12 (k6) and 3.14 (Playwright),
both cut and said to be cut in the README; 3.10 (bulk import) is struck as never having been a
customer requirement.

Live: https://salary-management-ui-5bp6.onrender.com — `hr.manager@acme.example` / `demo-password`.
Both services are Render free-tier and spin down; warm `/actuator/health` first (67 s measured,
over 120 s after a long idle).

**187 unit + 126 integration + 106 Angular specs green.** `mvn clean verify` 2:18, production
`ng build` clean, mutation 91% (213/234 killed, strength 98%) against a 70% threshold.

## Landed today

Twenty-three commits, `9970c59` through `1b6e887`:

- `feat: add the compensation dashboard screen` — 3.9, four KPI cards and four filters
- `docs: quote the measured mutation score rather than the threshold` — D138
- `docs: correct the percentile rule where it was still stated the old way`
- `docs: record that plan 2.15 is deliberately unmet, not pending` — D139
- `fix: paint only the dashboard answer that matches the filters on screen` — D140, the race
- `refactor: move the shared filter options out of the directory feature`
- `docs: stop claiming tests assert the query plans` — no test runs `EXPLAIN`
- `docs: record the review's pattern, and stop CLAUDE.md listing tools that do not exist` — D141, D142
- `feat: break payroll spend down by department, country or level` — 3.3
- `docs: record why the breakdown reads the rate date separately` — D143
- `feat: show how pay is spread within each role` — 3.4
- `fix: refuse to report a salary we have no rate for, instead of converting it at 1.0` — **ADR-0015**, D144–D146
- `fix: answer for each dashboard section, and stop claiming a date that is absent`
- `fix: refuse a pay change decided against a salary that has since moved` — D147, optimistic locking
- `fix: correct the documents around the lock, and the tests that overstated it` — D148–D151
- `feat: read the audit log through JPA, and measure the N+1 it starts with` — D152–D154
- `feat: show each employee's salary band, and keep a refused pay change on screen` — D155–D159
- `chore: re-seed the deployed database on the next boot` and its `Revert` — see D160
- `docs: correct the record on the deployed re-seed, which did work` — D160
- `docs: mark the credentials rotated, and note what rotation does not prove`
- `fix: make the production build compile, which is why the site never deployed` — D161
- `docs: make the README a submission document and STATUS match what shipped`

## Uncommitted

_(none — working tree clean, `main` and `origin/main` in sync)_

## Blockers

**1. ~~The demo video is not recorded.~~ Closed 16 Sep** — recorded and linked in the README. An
unauthenticated request to the Drive link returns 200 without redirecting to a sign-in page, which
is evidence it is publicly reachable, not proof the video plays for a stranger.

**2. `SPRING_DATASOURCE_URL` still has not been read back** — open, and not closed by the 16 Sep
redeploy. The string handed over in conversation was the `-pooler` host and it cannot be read from
this machine. The indirect evidence is now one boot stronger: removing `SPRING_PROFILES_ACTIVE`
forced a fresh container, and `DatabaseIdentityCheck` — which runs after migration and refuses to
start unless the migration pool is *not* `salary_app` and the application pool *is*, the exact
pooler failure D110 documents — passed on that cold boot too. That is still inference. It is also
weaker than it looks: PgBouncer leaks `SET ROLE` only when a server connection is actually reused
across clients, so a quiet boot can pass and a busy demo can fail. **Read the string in the Render
dashboard.** If it contains `-pooler`, drop that from the host and redeploy.

**3. ~~The rotated Neon password may not be on the Render service.~~ Closed 16 Sep.** Removing
`SPRING_PROFILES_ACTIVE` forced a redeploy, so the container now serving built its Hikari pool from
nothing after the rotation — every connection it holds authenticated against Neon with the rotated
credential. That is the conclusive check this blocker itself prescribed, and the ambiguity it
described is gone: the old evidence was consistent with a service that would die on its next cold
start, and the cold start has now happened.

**3b. The seed profile is removed, and the removal is not yet proved by data.**
`SPRING_PROFILES_ACTIVE=seed` was deleted from the Render service on 16 Sep. It was a dashboard
change, so like Blocker 2 it cannot be read back from here — recorded on the operator's word, not
verified. The seed is deterministic, so every figure the API returns is identical whether or not it
re-seeded on boot, and no total can distinguish the two. **The one check that can:** change a
salary, force a cold start, and confirm the change survived. Until that runs, what is known is that
the variable was removed, not that a reviewer's edit will still be there tomorrow.

**4. CI runs neither the Angular suite nor `ng build`.** `.github/workflows/ci.yml` runs `mvnw
verify` and pitest only. The UI silently stayed on an old bundle for hours today because `ng build`
was failing while all 101 specs passed (D161). Nothing in CI would have caught it.

**5. `PLAN.md` 2.15 is deliberately unmet** (D139). `compensation` and `bulkimport` packages do not
exist; no empty ones were created to make `failOnEmptyShould` pass.

**6. Domain rejections still return 400 where `04-API-DESIGN.md` says 422/409** (D112), except the
two fixed today: a concurrent change is 409 with its own `type` URI, and an unconvertible currency
is 422.

**7. The documented concurrency contract is unbuilt.** `04-API-DESIGN.md` promises `Idempotency-Key`
on salary changes and `ETag`/`If-Match` on employee updates. Neither exists; a client sending
`If-Match` today is ignored. The lost update itself is closed (D147).

## Decisions recorded

**D138–D163** (163 rows total), plus **ADR-0015**. D163 supersedes D015. No rows remain on `Probation`.

Load-bearing for the video:

- **ADR-0015** — an unconvertible salary refuses the whole answer rather than converting at 1.0.
  Found by review, reproduced against seeded data: `?currency=EUR` reported India at EUR 4.93bn,
  and one newer rate row moved global payroll from 1.21bn to 6.19bn on the default path.
- **D147** — optimistic locking is a compare-and-set on the salary itself, no version column,
  proved by reverting the clause and watching two threads both succeed.
- **D153** — an N+1's cost scales with distinct associated rows, not rows read: 2 / 31 / 1.
- **D155** — seeded bands sit around current pay; 61% of the org read "above maximum" before that.
- **D160** — verify a deploy by asking the new build for something only it can answer.
- **D161** — `ng test` is not `ng build`.

## Next action

`PLAN.md` task **3.22 — send the repository link.** 3.21 and 3.19 are done: the video is recorded
and linked.

Before sending, settle Blockers 2, 3 and the seed-profile removal in the Render dashboard — none of
the three can be read or changed from this machine — and run 3.20, the final deploy smoke test.

## Gotchas

- **Builds need `JAVA_HOME=$HOME/.jdks/jdk-21`.** Node 22 is at `~/.nodejs/current/bin`.
- **One Maven process at a time.** Two concurrent runs corrupt `target/checkstyle-result.xml` and
  produce failures that have nothing to do with the code. Cost two false alarms today.
- **`pkill -f <broad pattern>` kills your own tooling.** `pkill -f "spring-boot:run"` and
  `pkill -f "until curl"` each killed the shell that issued them. Match on something narrower.
- **Run `ng build`, not only `ng test`, before pushing UI changes** (D161). Karma compiles JIT;
  the production build is AOT with strict templates and rejects things the suite accepts.
- **Verify a deploy by asking the new build for something only it can answer** (D160). Render keeps
  the previous container serving until the replacement is healthy, so `/actuator/health` 200 can be
  the old version answering — which is how a successful re-seed was misread as a failed one.
- **Render applies `render.yaml` envVars on blueprint sync, and also on auto-deploy** — the seed
  profile did take effect, and removing it from `render.yaml` did *not* remove it from the service:
  it survived as a dashboard variable from 12 to 16 Sep, truncating and rebuilding on every cold
  start for four days. A blueprint edit adds and updates; deleting needs the dashboard.
- **Never point the datasource at Neon's `-pooler` host** (D110).
- **Angular templates:** `as` binds only on the primary `@if`, never on `@else if`; `@` in template
  text must be `&#64;`; `type="number"` with `ngModel` routes the value through `parseFloat` and
  clears the field when a decimal point is typed (D156).
- **Material renders `<mat-error>` only when its own control is invalid** — a server-side refusal
  is not, so it needs its own element.
- **A spy rebuilt inside a `render()` helper discards per-test overrides.** Parameterise the helper.
- **The Testcontainers database is shared and the app role has no `DELETE`.** No test may assume an
  empty table; pin assertions to values nothing else generates, and sweep anything committed outside
  a transaction — including into the *next* run, because the container is reused.
- **A test that spawns threads must carry the `SecurityContext` onto them**; `@WithMockUser` only
  binds the test thread.
- **PMD's `GuardLogStatement` fires on any log argument that is a method call.** Hoist it first.
- **Spotless deletes an import the moment nothing uses it.** Add an import and its first use in the
  same edit, and re-run `spotless:apply` before assuming a replacement failed — a reformat is why
  several scripted edits silently did not match today.
- **A bare `? IS NULL` is rejected by PostgreSQL.** Optional filters need `CAST(:p AS text) IS NULL`.
- **An unquoted `key: value` inside a YAML description breaks the file.** `defined: false` in prose
  made `openapi.yaml` unparseable.

_`main` and `origin/main` at `1b6e887` when this was written._
