---
description: Craftsmanship review of the current diff before committing
---

Review `git diff` (staged and unstaged) against this checklist. Be adversarial — your job is to find
what is wrong, not to approve. Report findings ordered by severity, with file and line. If the diff
is clean, say so in one line rather than inventing findings.

**Correctness**
- Money: any `double`/`float`? Any cross-currency arithmetic without an explicit rate? Rounding mode set?
- Intervals: half-open `[from, to)` respected? Boundary cases (equal dates, open-ended, future-dated) handled?
- Any `LocalDate.now()` / `Instant.now()` outside an adapter? `Clock` must be injected.
- Does anything overwrite compensation history instead of appending?
- Null handling: `Optional` at boundaries, no null returns from public methods.

**Architecture**
- Framework imports in `..domain..`?
- Port declared in `application`, not next to its implementation?
- JPA entity escaping its persistence package?
- Business logic in a controller or a service that should be in a domain object?
- `analytics` touching a write path?

**Performance**
- Loading entities to aggregate in Java instead of querying?
- A new query without an index, or a list endpoint without keyset paging?
- Potential N+1? Does the integration test declare a statement count?

**Tests**
- Does the test name state a behaviour, or a method name?
- Does it assert an outcome, or a mock interaction?
- Would it still pass if the implementation were subtly wrong? Name the mutation that would survive.
- Boundary cases covered, or only the happy path?
- Deterministic — no real clock, no random, no ordering dependency?

**Security & hygiene**
- Missing `@PreAuthorize` on a use case? String-concatenated SQL? Salary values in a log line?
  Secret in the diff? Debug code, commented-out code, stray formatting churn?

End with: **APPROVE** or **CHANGES REQUIRED**, and if the latter, the smallest set of fixes needed.
