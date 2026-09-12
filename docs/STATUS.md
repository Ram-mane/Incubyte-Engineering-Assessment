# Session status

> Written at the end of each session by `/wrap`, read at the start of each by `/prime`.
> Hand-written, so `git log` wins any disagreement.

**Last updated:** 2026-09-12, end of Day 3. Submission day.

## Position

**The product is built, deployed and verified against the deployed instance.** What remains is the
demo video, which has not been recorded.

Live: https://salary-management-ui-5bp6.onrender.com — sign in as `hr.manager@acme.example` /
`demo-password`. Both services are free-tier and spin down; warm
`/actuator/health` before demoing (67 s measured, over 120 s after a long idle).

**187 unit + 126 integration + 101 Angular specs green**, `mvn clean verify` in 2:18, production
`ng build` clean. Mutation 89% (178/201 killed, strength 96%) against a 70% threshold.

## What shipped today

Day 3 in order: the dashboard screen (3.9), breakdown (3.3), distribution (3.4), the FX refusal
fix, optimistic locking, the JPA read-path slice, salary bands displayed, the change-pay dialog
rebuild, and the directory's empty state.

Five code reviews ran against this work and every one found something real. The pattern worth
carrying forward is that **three of them found tests of mine that could not fail** — a median where
the mean would have passed, a float check after rounding had already happened, a concurrency test
asserting the scheduler, a reload assertion that never looked at what was reloaded. A green suite
was not evidence. D141, D148, D149, D153 and D161 record each one.

## Verified on the deployed instance

Not on localhost. Each checked through a headless browser against the live URL:

- Dashboard reads $1,213,179,157 / 10,000 / $121,318 / $113,924
- Employee detail shows the band (A$85,000 / A$100,000 / A$120,000, Within band, compa-ratio 1.0672)
- The change-pay dialog shows the band, and typing `1500000.5` keeps every character
- Negative, zero, letters and empty amounts are each refused with the dialog open and the message
  on the field
- An empty search renders "No employees match your search." inside the table body
- "Changed by" renders the author's email for both HR_MANAGER and HR_ANALYST

## Blockers

**1. The demo video is not recorded.** The only thing between this and submission.

**2. `SPRING_DATASOURCE_URL` on Render has not been read back.** It cannot be read from this
machine. The evidence that it is the direct Neon host and not the `-pooler` one is strong but
indirect: `DatabaseIdentityCheck` runs after migration on every boot and refuses to start unless
the migration pool is *not* `salary_app` and the application pool *is* — which is precisely the
pooler failure D110 documents — and the service has come up cleanly through several deploys today.
That is inference, not the string. **Confirm it in the dashboard before recording.**

**3. The new Neon password may not be on the Render service.** Rotated today. The API kept serving
afterwards and 16 concurrent queries all succeeded, but Hikari's pool is 10 connections and those
may predate the rotation. The conclusive check is Manual Deploy → Restart service, then load the
dashboard. Do it before recording, not after.

**4. `PLAN.md` 2.15 is deliberately unmet** (D139) — `compensation` and `bulkimport` packages do
not exist and no empty ones were created to make `failOnEmptyShould` pass.

**5. Domain rejections return 400 where the API doc says 422/409** (D112), except the two that were
fixed: the concurrent-change conflict is 409 with its own `type`, and an unconvertible currency is
422.

## Not built, and said so in the README

CSV bulk import (in scope, the largest gap), Playwright, k6, band position as a directory column,
and the `ETag`/`If-Match` concurrency contract `04-API-DESIGN.md` promises. All are listed under
"Known issues and what I would do next" rather than left for a reviewer to discover.

## Decisions recorded

**D001–D161**, plus **ADR-0001 to ADR-0015**. Load-bearing for the video:

- **ADR-0015** — an unconvertible salary refuses the whole answer rather than converting at 1.0.
  Found by review, reproduced against the seeded data: `?currency=EUR` reported India at EUR 4.93bn.
- **D147** — optimistic locking is a compare-and-set on the salary itself, no version column,
  proved by reverting the clause and watching two threads both succeed.
- **D155** — seeded bands sit around current pay, not starting pay; 61% of the org read
  "above maximum" before that was fixed.
- **D161** — `ng test` is not `ng build`. The UI deploy silently stayed on an old bundle for hours
  because the production compiler was failing while 101 specs passed.

## Gotchas

- **Builds need `JAVA_HOME=$HOME/.jdks/jdk-21`.** Node 22 is at `~/.nodejs/current/bin`.
- **Run one Maven process at a time.** Two concurrent runs corrupt `target/checkstyle-result.xml`
  and produce failures that have nothing to do with the code. This cost two false alarms.
- **Run `ng build`, not only `ng test`, before pushing UI changes** (D161).
- **Verify a deploy by asking the new build for something only it can answer** (D160). Render keeps
  the old container serving until the replacement is healthy, so a `/actuator/health` 200 can be
  the previous version answering.
- **Never point the datasource at Neon's `-pooler` host** (D110).
- **The Testcontainers database is shared and the app role has no `DELETE`.** No test may assume an
  empty table; pin assertions to a value nothing else generates, and sweep anything committed
  outside a transaction.
- **Any test that calls a use case needs `@WithMockUser`**, and a test that spawns threads must
  carry the `SecurityContext` onto them explicitly.
- **PMD's `GuardLogStatement` fires on any log argument that is a method call.** Hoist it first.
- **Spotless deletes an import the moment nothing uses it.** Add an import and its first use in the
  same edit.
- **A bare `? IS NULL` is rejected by PostgreSQL.** Optional filters need `CAST(:p AS text) IS NULL`.
- **Angular templates read `@` as control flow** — an email address in template text must be `&#64;`
  — and `as` only binds on the primary `@if`, never on `@else if`.

## Next action

Record the demo video (3–5 min), to the script in `PLAN.md`. Warm the API first. Before recording,
confirm the two dashboard items in Blockers 2 and 3.

Then: README video link, final deploy smoke test, send the repository link.

_`main` and `origin/main` at `6fde703` when this was written._
