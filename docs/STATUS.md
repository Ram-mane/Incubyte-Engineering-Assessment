# Session status

> Written at the end of each session by `/wrap`, read at the start of each by `/prime`.
> Hand-written, so `git log` wins any disagreement.

**Last updated:** 2026-09-16. Post-submission-build session: one UI defect, one UI polish item,
one deploy config change, and a long documentation-accuracy pass.

## Position

**Day 3. The build is feature-complete and the demo video is recorded and linked.** The only PLAN
task left is **3.22 — send the repository link**, with 3.20 (final deploy smoke test) ahead of it.

Live: https://salary-management-ui-5bp6.onrender.com — `hr.manager@acme.example` / `demo-password`.
API: https://salary-management-api-bv03.onrender.com. Both Render free-tier and spin down; warm
`/actuator/health` first. Verified live on 16 Sep: login works, `/dashboard/summary` returns
headcount 10,000 and `USD 1,213,179,157.20`.

Video: https://drive.google.com/file/d/1c5UlFRHVQilX_U-pOAByc1Wnooxd9o41/view?usp=sharing

**187 unit + 126 integration + 106 Angular specs green.** `mvn clean verify` BUILD SUCCESS in 1:49,
production `ng build` clean, mutation **213/234 killed (91%), test strength 98%**, line coverage
305/329, against a 70% threshold.

PLAN 3.10 (CSV bulk import) is **struck**, not pending — see D163. Still not built and cut:
3.5 (band column in the directory), 3.7 (`EXPLAIN`-asserting tests), 3.7b, 3.8 (Caffeine),
3.11–3.12 (k6), 3.14 (Playwright), 3.15 (accessibility pass), 3.17 (Swagger UI). All are in the
README's known issues; none is one of the customer's ten points.

## Landed today

Nine commits, `fe0f62a` through `7de4811`:

- `fix: stop the band panel answering for the previous salary` — the panel kept the old verdict for
  ~4s after a raise while the header showed the new figure
- `feat: write wire constants the way a person reads them` — `EnumLabelPipe`; MERIT / HR_MANAGER
  render as prose, wire values unchanged
- `chore: stop returning visitors being served a stale index.html` — `Cache-Control: no-cache`
  header in `render.yaml`
- `docs: stop claiming a customer asked for bulk import, and record the second pass` — R1–R12
- `docs: describe the time discipline that is built, not a weaker one` — "Clock injected
  everywhere" replaced; the domain takes an `Instant`
- `docs: quote the mutation score this build actually produces` — 178/201/96% was stale
- `docs: link the demo video, and close the blocker that was waiting on it`
- `docs: close what the redeploy settled, and say what it did not`
- `docs: retract the bulk-import claim where a reviewer reads it first`

## Uncommitted

_(none — working tree clean, `main` and `origin/main` in sync)_

## Blockers

**1. `SPRING_DATASOURCE_URL` has never been read back, and the string handed over in conversation
was the `-pooler` host.** Cannot be read from this machine. The evidence the service is on the
direct host is indirect: `DatabaseIdentityCheck` runs after migration on every boot and refuses to
start unless the migration pool is *not* `salary_app` and the application pool *is* — the exact
pooler failure D110 documents — and it passed again on the 16 Sep cold boot. That is inference, and
weaker than it looks: PgBouncer leaks `SET ROLE` only when a server connection is actually reused
between clients, so a quiet boot passes and a busy demo is where it bites. **Read the string in the
Render dashboard.** If it contains `-pooler`, drop that from the host and redeploy.

**2. The seed-profile removal is not proved by data.** `SPRING_PROFILES_ACTIVE=seed` was deleted
from the Render service on 16 Sep. Dashboard change, so like Blocker 1 it cannot be read back here
— recorded on the operator's word. And no API response can confirm it: the seed is deterministic,
so every figure is identical whether or not it re-seeded on boot. **The one check that works:**
change a salary, force a cold start (wait out the spin-down or redeploy), confirm the change
survived. Until that runs, what is known is that the variable was removed, not that a reviewer's
edit will still be there tomorrow. This is the last unverified thing in the submission and it is
the one a reviewer would find the hard way.

**3. Mutation runs are slow enough to look hung.** `./mvnw -Pmutation test` sends **183 test
classes** to the minion, Testcontainers ITs included. Two runs were killed — one by session
teardown, one by a 9.5-minute timeout — both sitting at `Created 43 mutation test units` with no
output. The third, left unbounded, finished fine. **Do not conclude it has hung.** A killed run also
leaves a truncated `target/pit-reports/mutations.xml` that parses to plausible, wrong numbers
(199 mutations / 91% / 97%); check the file ends with `</mutations>` before believing it.

**4. CI runs neither the Angular suite nor `ng build`.** `.github/workflows/ci.yml` runs `mvnw
verify` and pitest only. D161's failure — `ng build` broken while all specs passed — would still
get through today.

**5. `PLAN.md` 2.15 is deliberately unmet** (D139). `compensation` and `bulkimport` packages do not
exist; no empty ones were created to make `failOnEmptyShould` pass.

**6. Domain rejections still return 400 where `04-API-DESIGN.md` says 422/409** (D112), except the
two fixed: concurrent change is 409 with its own `type` URI, unconvertible currency is 422.

**7. The documented concurrency contract is unbuilt.** `04-API-DESIGN.md` promises `Idempotency-Key`
on salary changes and `ETag`/`If-Match` on employee updates. Neither exists; a client sending
`If-Match` today is ignored. The lost update itself is closed (D147).

**8. The `render.yaml` no-cache header is unverified.** Added 13 Sep; it applies on blueprint sync
and nobody has checked the response headers on the deployed static site.

**Closed this session:** the demo video (recorded, linked, and the Drive URL answers 200 to an
unauthenticated request); the rotated Neon password (the 16 Sep redeploy built a fresh Hikari pool
that authenticated entirely post-rotation — the conclusive check the old blocker itself prescribed).

## Decisions recorded

**D162** — "most of them confirmed with the customer" counted against the replies: eight of eleven.
**D163** — bulk CSV import was never a customer requirement; **supersedes D015**, which said it was
kept in scope and was still `Active`.

163 rows total. No rows on `Probation`.

Load-bearing for the interview:

- **D163** — a requirement I inferred hardened into scope, a PLAN task, and a README line calling it
  "the largest thing missing". The customer was about to be told the biggest hole in their build was
  something they never asked for.
- **D162** — an accurate adjective nobody had ever checked is one repo change from being a wrong one.
- **ADR-0015** — an unconvertible salary refuses the whole answer rather than converting at 1.0.
- **D147** — optimistic locking is a compare-and-set on the salary itself, no version column.
- **D153** — an N+1's cost scales with distinct associated rows, not rows read: 2 / 31 / 1.
- **D160** — verify a deploy by asking the new build for something only it can answer.
- **D161** — `ng test` is not `ng build`.

## Next action

`PLAN.md` task **3.22 — "Send the repo link"**.

Before sending: run **3.20** (final deploy smoke test), and settle Blockers 1 and 2 in the Render
dashboard — neither can be read or changed from this machine.

## Gotchas

- **Builds need `JAVA_HOME=$HOME/.jdks/jdk-21`.** Node 22 is at `~/.nodejs/current/bin`.
- **One Maven process at a time.** Two concurrent runs corrupt `target/checkstyle-result.xml` and
  produce failures that have nothing to do with the code.
- **A killed Pitest run leaves a truncated report that still parses.** Check for the closing
  `</mutations>` tag before quoting any number from `target/pit-reports/mutations.xml`.
- **`mvn -q clean verify` hides the summary.** The tail is mid-run Spring logs, not the result —
  redirect to a file and grep for `BUILD`, or read `target/{surefire,failsafe}-reports/*.txt`.
- **`pkill -f <broad pattern>` kills your own tooling.** `pkill -f "spring-boot:run"` and
  `pkill -f "until curl"` each killed the shell that issued them. Match on something narrower.
- **Run `ng build`, not only `ng test`, before pushing UI changes** (D161). Karma compiles JIT;
  the production build is AOT with strict templates and rejects things the suite accepts.
- **Verify a deploy by asking the new build for something only it can answer** (D160). Render keeps
  the previous container serving until the replacement is healthy, so `/actuator/health` 200 can be
  the old version answering.
- **Render applies `render.yaml` envVars on blueprint sync and on auto-deploy** — but removing one
  from `render.yaml` does *not* remove it from the service. `SPRING_PROFILES_ACTIVE=seed` survived
  as a dashboard variable from 12 to 16 Sep, truncating and rebuilding on every cold start for four
  days. A blueprint edit adds and updates; deleting needs the dashboard.
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
  same edit, and re-run `spotless:apply` before assuming a replacement failed.
- **A bare `? IS NULL` is rejected by PostgreSQL.** Optional filters need `CAST(:p AS text) IS NULL`.
- **An unquoted `key: value` inside a YAML description breaks the file.** `defined: false` in prose
  made `openapi.yaml` unparseable.
- **A stale number fails nothing.** Four places repeated a mutation score measured four days and
  several features earlier. Re-measure before quoting, and grep for the figure — not the sentence —
  because the same number hides in a comment, a bullet and a command block.

_`main` and `origin/main` at `7de4811` when this was written._
