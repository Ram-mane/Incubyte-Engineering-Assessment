# Session status

> Written at the end of each session by `/wrap`, read at the start of each by `/prime`.
> Hand-written, so `git log` wins any disagreement.

**Last updated:** 2026-09-11, end of Day 1 afternoon

## Position
**Day 1 complete.** Tasks 1.7 through 1.12 are all committed and pushed: `Money`, `ExchangeRate`,
the `Employee` aggregate, `changeSalaryTo` with its audit revision, the audit-chain property, and
`SalaryBand`/`BandPosition`. Day 2 has not started.

132 unit tests + 2 integration tests green. Mutation 93% on 107 (threshold 70). 10 ArchUnit rules.
CI green on every push.

## Landed today
- `feat: add employee aggregate`
- `test: keep generated record methods out of the mutation gate`
- `feat: add salary change with mandatory audit revision`
- `test: prove audit chain consistency with property tests`
- `feat: add salary bands and band position`
- `test: enforce that modules do not reach into each other`
- `test: assert the natural key a band is found by`

## Uncommitted
_(none — working tree clean, local and `origin/main` identical at `da7441d`)_

## Blockers
- **The `test-auditor` subagent run is outstanding.** The `mcs-day1-afternoon` skill requires it
  after 1.12. It was launched against the ten domain test files and had not reported when the
  session closed, so **no test-quality audit has been read**. Re-run it before Day 2 writes
  persistence tests on top of these: the honest question after 132 green tests is whether any
  would fail if the code were subtly wrong, and three areas are known to be outside the mutation
  gate (see Gotchas).
- Nothing else broken. Both deployed services were last confirmed live on 10 Sep
  (`salary-management-api-bv03`, `salary-management-ui-5bp6`); neither has been redeployed since,
  because Day 1's work is all domain code with no endpoint.

## Decisions recorded
D067–D087, twenty-one rows. The load-bearing ones for Day 2:

- **D075** — `changeSalaryTo` takes the `Instant` to record, not a `Clock`. The application layer
  resolves its injected clock at the boundary. `CLAUDE.md` now states the goal (no ambient time)
  rather than naming a mechanism.
- **D079, D083** — `SalaryRevision` and `SalaryBand` carry no identity. `BIGSERIAL` PK in both
  tables, absent from the domain. Entities that are referenced need identity; append-only records
  and natural-key lookups do not.
- **D078** — `change_reason` is `varchar` + `CHECK`, not a PostgreSQL native enum. Decided so 2.2
  does not revisit it.
- **D071, D083, D085** — three corrections to `02-DOMAIN-MODEL.md`: `DepartmentId` not
  `Department`, no `BandId`, and I10 tightened to `0 < min ≤ mid ≤ max`.
- **D082** — a type enters `shared` only when two or more modules genuinely consume it.
- **D080** — domain exception messages name the employee and the currencies, never an amount.

`Money.convertTo` remains on **probation** (D039): still no production caller, which is expected —
normalisation is SQL-side. The condition settles at the end of Day 3, not now.

## Next action
`PLAN.md` task 2.1 — "Flyway V1: department, app_user, employee (with embedded salary)",
commit `feat: add employee schema`.

Note the migration numbering: V1 is already taken by `V1__enable_required_extensions.sql`
(`pg_trgm`), so 2.1's schema is **V2** and every subsequent number shifts by one.

## Gotchas
- **Builds need `JAVA_HOME=$HOME/.jdks/jdk-21`.** Temurin, installed by hand — the system JDK is 25
  and the enforcer rejects anything outside 21.x. Node 22 is at `~/.nodejs/current/bin`. Neither is
  on the default `PATH`.
- **The mutation score does not cover everything, and the README quotes it.** `07-TEST-STRATEGY.md`
  §5 has the full list: Pitest mutates no record compact constructor unless `-FRECORD` is set;
  `equals`/`hashCode`/`toString` are excluded by method name (D074), which also excludes
  `Employee`'s hand-written identity equality; coverage arising from a test class's static
  initialiser is attributed to no test, so Pitest reports `SURVIVED` for `CurrencyCode`'s guard that
  34 tests actually kill; and `Money`'s rounding policy has no mutable construct at all. Four manual
  probes stand in — **re-run them after any change to `Money`'s or `Employee`'s constructor.**
- **Six gates have now been found reporting success while checking nothing** (D043, D044, D046,
  D065, D087). The pattern holds: a rule written in prose is not a rule. Assume any gate that has
  never failed is decoration until a deliberate violation proves otherwise.
- **Every quality-gate change so far has cost a second commit**, because adding domain code changed
  what the gates see: eight records took mutation from 100% to 58%, and PMD fired eleven times on
  the one hand-written aggregate. Expect the same when JPA entities arrive in 2.5.
- `.jqwik-database` is gitignored — jqwik writes failed property samples there to replay them first.
- The `mcs-day*` command files were corrected on install and may still contain stale briefs for
  Day 2 and Day 3; check them against `docs/DECISIONS.md` before following them.
