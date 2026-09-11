# Session status

> Written at the end of each session by `/wrap`, read at the start of each by `/prime`.
> Hand-written, so `git log` wins any disagreement.

**Last updated:** 2026-09-11, end of Day 1 afternoon

## Position
**Day 1 complete.** Tasks 1.7 through 1.12 are all committed and pushed: `Money`, `ExchangeRate`,
the `Employee` aggregate, `changeSalaryTo` with its audit revision, the audit-chain property, and
`SalaryBand`/`BandPosition`. Day 2 has not started.

141 unit tests + 2 integration tests green. Mutation gate above threshold; read its caveats in
`07-TEST-STRATEGY.md` §5 before quoting the number. 10 ArchUnit rules.
CI green on every push.

## Landed today
- `feat: add employee aggregate`
- `test: keep generated record methods out of the mutation gate`
- `feat: add salary change with mandatory audit revision`
- `test: prove audit chain consistency with property tests`
- `feat: add salary bands and band position`
- `test: enforce that modules do not reach into each other`
- `test: assert the natural key a band is found by`
- `docs: update session status`
- `test: close audit gaps in domain rejection paths`

## Uncommitted
_(none — working tree clean)_

## Blockers
_(none)_

The `test-auditor` run required after 1.12 is **complete**. It applied ten mutations to production
code and **eight survived**. Three were closed before the session ended; the rest are recorded in
`09-TRADEOFFS.md` under "Test gaps left open, and why".

What it found, and what was done:

- **Rejections half-applied.** Moving the assignment in `changeSalaryTo` above the TERMINATED,
  positivity and no-op guards passed 41 tests: a terminated employee's pay moved and no revision
  existed. Only the currency path asserted post-throw state. **Closed** — all five rejection paths
  are now a parameterised test asserting both the salary is unchanged and no revision was produced.
- **I9 was never exercised outside India.** Replacing `country.currency()` with the literal
  `"INR"` passed 104 tests, because every employee in the suite was Indian and
  `EmployeeMother.inGermany()` was dead code. **Closed** — a German employee is now paid, re-paid
  and refused rupees. This mattered for Day 2: the seeder populates six countries.
- **The band's zero-bound test tested one bound, not three.** It set all three to zero at once, so
  narrowing the guard to the midpoint passed 20 tests. **Closed** — three separate tests, one per
  bound.

All three mutations were re-applied afterwards and now fail the suite.

## Decisions recorded
D067–D089, twenty-three rows. The load-bearing ones for Day 2:

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
- **D088** — `requireNonNull` guards are deliberately not converted to `if`-throw yet. The example
  tests cover them; it is the measurement that is incomplete. Converting them later is legitimate
  on design grounds — an NPE is a JDK exception leaking as a domain validation failure — and only
  incidentally makes them mutable.

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
- **Seven gates have now been found reporting success while checking nothing** (D043, D044, D046,
  D065, D087, D088). The newest: the mutation score covers neither null guards (`requireNonNull`
  returns a value, so `VOID_METHOD_CALLS` does not remove it) nor `Money`'s arithmetic (`BigDecimal`
  ops are method calls, so no MATH mutator applies). The pattern holds: a rule written in prose is not a rule. Assume any gate that has
  never failed is decoration until a deliberate violation proves otherwise.
- **Every quality-gate change so far has cost a second commit**, because adding domain code changed
  what the gates see: eight records took mutation from 100% to 58%, and PMD fired eleven times on
  the one hand-written aggregate. Expect the same when JPA entities arrive in 2.5.
- `.jqwik-database` is gitignored — jqwik writes failed property samples there to replay them first.
- The `mcs-day*` command files were corrected on install and may still contain stale briefs for
  Day 2 and Day 3; check them against `docs/DECISIONS.md` before following them.
