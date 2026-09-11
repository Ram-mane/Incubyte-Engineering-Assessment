# Decision log

Every decision taken on this project and the reasoning behind it.

**Relationship to ADRs.** An ADR is for a decision that is expensive to reverse — one that would
cost a migration, a rewrite spanning more than one module, or a change to a published contract.
This log is for everything else: the smaller calls that shape the code and would otherwise survive
only in a chat transcript. Where a decision has an ADR, the log row points at it rather than
repeating it.

**Graduation rule.** If a row's *Why* cannot be said in one sentence, it is probably an ADR.
Write the ADR and reduce the row to a pointer.

**Status.** `Active` · `Superseded by Dnnn` · `Reversed` · `Probation` (will be deleted unless a
condition is met by a stated date)

IDs are chronological, so reading top to bottom shows how the design evolved. Grouping is by area,
so reading a section shows what governs one part of the system.

---

## Process and scope

| ID | Decision | Why | Status |
|---|---|---|---|
| D001 | Send ten clarifying questions before writing any code | The brief was open to interpretation and the assessment explicitly rewards clarifying. Three answers reversed decisions already made — asking cost one document, building on the inference would have cost two days | Active |
| D002 | Build to the customer's confirmed scope, not the inferred one | The role is titled Software Craftsperson; overriding written scope guidance demonstrates the opposite. [ADR-0012](adr/0012-scope-confirmed-with-customer.md) | Active |
| D003 | Keep the superseded temporal design in the repo rather than deleting it | A reversal made on evidence is more informative to a reviewer than a tidy history | Active |
| D004 | Angular 19 rather than React, despite the brief listing React first | The JD is Java/Angular; confirmed acceptable by the customer | Active |
| D005 | Three-day build, submit Saturday 12 Sep | Scope shrank ~40% after clarification, and the customer said early submission is prioritised | Active |
| D006 | Deploy to Render + Neon free tier | Public URL was the requirement; cost and setup time were not worth spending on AWS here | Active |
| D007 | Scale headroom is documented, not built | 1M employees is ~3M rows and ~2.5 GB — small for PostgreSQL. Building for it would contradict "not the most complex system". [06-SCALABILITY.md](06-SCALABILITY.md) gives eight steps with measurable triggers | Active |
| D008 | Reply to the customer confirming the six scope changes in writing | Removes ambiguity about what was built, and timestamps the start | Active |

## Product scope

| ID | Decision | Why | Status |
|---|---|---|---|
| D009 | Payroll processing out of scope | A separate regulated product per country; the persona's problem is understanding pay, not paying | Active |
| D010 | Dashboard with KPI cards and filters is the primary deliverable | Confirmed by the customer as the target; it is also what a spreadsheet cannot do | Active |
| D011 | Annual base salary only, in local currency | Confirmed. Model shaped so components can be added as typed siblings | Active |
| D012 | Salary bands displayed, never enforced | Confirmed — enforcement and alerting are future scope | Active |
| D013 | What-if simulation, review tracking and point-in-time snapshots dropped | Confirmed deferrable; each depends on history the customer said was unnecessary | Active |
| D014 | Approval workflow deferred, but the actor is recorded on every change | A workflow engine is orthogonal to the compensation domain. [ADR-0009](adr/0009-defer-approval-workflow.md) | Active |
| D015 | CSV bulk import kept in scope | The customer's data is currently in Excel; a product that cannot ingest it does not solve their problem | Active |
| D016 | Pay-equity analysis excluded | Needs protected-attribute data, a legal review and a privacy model that do not belong in a short build | Active |

## Data model

| ID | Decision | Why | Status |
|---|---|---|---|
| D017 | Salary as an append-only effective-dated history | Most interesting questions about pay are historical. [ADR-0002a](adr/0002-temporal-salary-model.superseded.md) | **Superseded by D018** |
| D018 | Current salary on the employee, plus an append-only audit log | The customer confirmed current state is sufficient, removing every capability that justified the temporal model. The log was kept because an unlogged mutation of pay data is a trust problem costing one table to avoid. [ADR-0002](adr/0002-current-salary-with-audit-log.md) | Active |
| D019 | `Employee.changeSalaryTo()` returns the `SalaryRevision`; no setter on `currentSalary` | Makes the audit guarantee structural — there is no code path that changes pay without producing a record | Active |
| D020 | Application DB role has `INSERT`/`SELECT` only on `salary_revision` | The append-only guarantee is enforced by the database, not only by the repository | Active |
| D021 | PostgreSQL 16, not SQLite | `numeric`, `percentile_cont`, `pg_trgm`, partial and covering indexes, per-table grants — all four things the schema leans on. [ADR-0003](adr/0003-postgres-over-sqlite.md) | Active |
| D022 | Keyset pagination, no `OFFSET` | O(1) at any depth and stable under concurrent writes. [ADR-0004](adr/0004-keyset-pagination.md) | Active |
| D023 | Trigram GIN index for name search | `ILIKE '%x%'` cannot use a B-tree; at 10k a sequential scan is fast enough to hide the mistake | Active |
| D024 | Native SQL projections for the dashboard, JPA for writes | Four KPI cards in one round trip; no entity loaded to be summed in Java. [ADR-0005](adr/0005-native-sql-for-analytics.md) | Active |
| D025 | Single tenant, no speculative `tenant_id` | Partially-implemented isolation is worse than none. [ADR-0010](adr/0010-single-tenant.md) | Active |

## Domain — Money and currency

| ID | Decision | Why | Status |
|---|---|---|---|
| D026 | `Money` as a value object, `BigDecimal` only, cross-currency arithmetic throws | Six currencies and every dashboard total crosses them. [ADR-0006](adr/0006-money-value-object.md) | Active |
| D027 | `Money` permits zero and negative; `minus` is total | Reverses an error in the domain doc. Two concrete bugs the alternative causes: `Money.zero(USD)` for an empty dashboard filter, and `salary.minus(band.min())` throwing for exactly the below-min employees the feature exists to surface | Active |
| D028 | I1 reattributed from `Money` to `Employee.changeSalaryTo` + DB `CHECK` | "A salary must be positive" is a rule about salaries, not about money. Naming a positive-only type `Money` is a lie | Active |
| D029 | Scale from `java.util.Currency.getDefaultFractionDigits()`, not a hardcoded map | Correct for every currency, no second source of truth, and gives ISO-4217 validation on `CurrencyCode` free | Active |
| D030 | Reject currencies whose fraction digits are −1 | Pseudo-currencies (XAU, XDR, XXX) would otherwise reach `setScale` with −1 | Active |
| D031 | `Money.of(String, ...)` and `Money.of(BigDecimal, ...)`; no `double`/`float` overload | `new BigDecimal(0.1)` is a real trap. Absence of the overload is a stronger guarantee than a lint rule | Active |
| D032 | Compact constructor normalises scale with HALF_EVEN rather than rejecting | Arithmetic results must round somewhere; rejecting forces callers to round first, which is where rounding bugs live. Over-precise user input is rejected at the DTO edge | Active |
| D033 | `convertTo` deferred from 1.7 to 1.8 | A minimal `ExchangeRate` in 1.7 reshaped in 1.8 means two commits on one type where the first is throwaway | Active |
| D034 | `isGreaterThan` written, then deleted; `isPositive()` only, arriving with I1's test in 1.10 | No test demanded it. Narrowest method the test requires | Active |
| D035 | No Java-side dated rate lookup in 1.8 | Normalisation happens in the SQL join; a Java rate table would be code the dashboard never calls. Date resolution stays behind the `ExchangeRateProvider` port for 2.x | Active |
| D036 | `convertTo` rejects a wrong-direction rate rather than inverting it | "No implicit conversion" covers direction. Silent inversion turns a bad seed row into plausible-but-wrong numbers, and `1/rate` adds invisible rounding error | Active |
| D037 | Same-currency conversion returns `this`, no rate required | Identity is not conversion. Otherwise the compa-ratio path needs INR→INR rows nobody will seed | Active |
| D038 | `ExchangeRate` rejects a rate ≤ 0 | A zero rate silently zeroes a payroll total | Active |
| D039 | `Money.convertTo` on **probation** — delete if it has no production caller by end of Day 3 | Normalisation is SQL-side, so it may never be called. It exists now because the rounding policy needs somewhere to live. Same standard applied to `isGreaterThan` (D034) | **Probation** |
| D058 | Cross-currency totals are summed per currency and converted once; per-row conversion is prohibited | [ADR-0013](adr/0013-sum-then-convert-for-currency-totals.md) | Active |
| D059 | Two properties guard it: P1 exact and order-independent, P2 bounded and named as a prohibition | [ADR-0013](adr/0013-sum-then-convert-for-currency-totals.md) | Active |
| D060 | The documented "within one minor unit" property bound was arithmetically false; the real bound is `0.005 × (n + currencies)` | A fixed one-unit bound holds only for lists short enough not to expose it, and jqwik finds the counterexample immediately. [ADR-0013](adr/0013-sum-then-convert-for-currency-totals.md) | Active |

## Domain — Employee

| ID | Decision | Why | Status |
|---|---|---|---|
| D067 | `EmployeeId` is assigned by the application at construction, never generated by the database | A null id before persistence breaks equality for the entity's whole transient life — which bites in the seed generator, holding ten thousand unsaved employees before the first insert | Active |
| D068 | `CountryCode` rejects an ISO-3166 country the JDK has no currency for | Narrows valid input deliberately: Antarctica is a real country code with no currency, and rejecting it at construction means `currency()` never returns null or throws | Active |
| D069 | `EmailAddress` is lowercased at construction | Equality becomes case-insensitive and the stored value can differ from what was typed. Correct for email, but a decision rather than an accident | Active |
| D070 | `Employee` carries `department`, `jobTitle` and `seniorityLevel` from 1.9; `employmentType` and `managerId` deferred | The first three are the filter dimensions the customer named (D010) and every directory and dashboard query groups on them — a known consumer 24 hours away is scope, not speculation, and the reshape would otherwise land when there is least slack. The other two are display-only; adding a column later is a migration, not a reshape | Active |
| D071 | `Employee` references its department by `DepartmentId`, not by a `Department` value | `05-DATA-MODEL.md` has `department_id` as an FK to a department table, and an aggregate referencing another by identity keeps the department name from being copied onto ten thousand employees. Diverges from the `Department department` in the 02-DOMAIN-MODEL class diagram | Active |
| D072 | The test for I1, `changing_salary_to_zero_is_rejected`, lands with 1.10 rather than 1.7 | I1 moved off `Money` and onto `Employee.changeSalaryTo` (D028), so its test belongs where the rule now lives. Carried over from the root `STATUS.md` before that file was deleted | Active |
| D040 | I8 (terminated employee) enforced in `Employee`, not `ChangeSalaryUseCase` | The aggregate holds `status`, so it can enforce the rule without a collaborator — same basis as I6 and I9. In the use case, bulk import or any second entry point could bypass it | Active |
| D041 | I8 enforced on `status == TERMINATED`, not `terminationDate` arithmetic | Date arithmetic drags a `Clock` into the check and raises a future-dated-termination question nothing in scope needs answered | Active |

## Quality gates

| ID | Decision | Why | Status |
|---|---|---|---|
| D042 | Quality gates wired into the build before any feature code | A JaCoCo or Pitest threshold is easy to satisfy from zero and painful to retrofit | Active |
| D043 | Gates must be *proved* to fail, not assumed to work | A gate that has never failed is decoration. Three gates were found reporting success while checking nothing | Active |
| D044 | Mutation scope extended to `shared` | `Money` lives in `shared`; the gate targeted `domain` and `application` only, so it reported zero mutations and passed vacuously while claiming the pay arithmetic was verified | Active |
| D045 | Framework-freedom rule changed from a denylist to an allowlist, covering `domain` **and** `shared` | Proved by putting `@Component` in `shared` and watching all eight rules pass. Domain imports shared, so framework code reached the domain through the one package the rules did not check. A denylist also misses anything unenumerated — slf4j was on the classpath and unnamed | Active |
| D046 | `failOnEmptyShould=true` scheduled as the final task of Day 2 | Five of the eight rules match zero classes because their packages do not exist yet — legitimate now, but a later package rename would silently disable a rule forever | Active |
| D047 | PMD `MissingSerialVersionUID` excluded for domain exception types, scoped, with the reason in the ruleset XML | These exceptions are never serialized. A `serialVersionUID = 1L` nobody will ever bump is a false maintenance signal, worse than absence | Active |
| D048 | Mutation testing at 70% on domain, with JaCoCo ungated on adapters | Line coverage says a line ran; mutation coverage says a test would notice if it were wrong. Chasing coverage on generated mappers produces tests nobody should read | Active |
| D049 | Testcontainers PostgreSQL rather than H2 for tests | Tests must exercise the engine, dialect and constraints that production uses | Active |
| D050 | Add a Day 3 test asserting the SQL aggregate and a Java computation agree to the minor unit | The jqwik property proves a rounding policy PostgreSQL is not bound by. Without this the property stays green while the dashboard reports a different total | **Superseded by D061** |
| D061 | PLAN 3.7b asserts **exact** equality against a full-precision `BigDecimal` reference that mirrors the SQL — accumulated across all currencies unrounded, rounded once at the end — and the reference must not use `Money` | "Agree to the minor unit" (D050) was still a tolerance, and 0.005 × currencies is three cents at six currencies, not one. `Money` rounds on construction (D032), so it represents amounts and cannot be an accumulator | Active |

## Working method

| ID | Decision | Why | Status |
|---|---|---|---|
| D051 | Tests written and approved before implementation, every task | AI output is accepted because a test passes, never because the code looks plausible | Active |
| D052 | One commit per task; gate fixes get their own commit; doc changes describing the same behaviour fold in | The history is graded. A gate defect exists independently of the feature that exposed it; an invariant's doc line does not | Active |
| D053 | Gate fixes ordered *after* the code that makes them non-vacuous | A gate-fix-only commit would have failed on "no mutations found" — a red intermediate commit is worse than imperfect ordering | Active |
| D054 | `/prime` at the start of every session, `/wrap` at the end | `docs/STATUS.md` is the handoff; without it each session re-derives context | Active |
| D055 | `git log` wins where it disagrees with `STATUS.md` | The status file is hand-written and drifts; commits cannot | Active |
| D056 | Every rejected or corrected AI output recorded in [08-AI-WORKFLOW.md](08-AI-WORKFLOW.md) | The failures are more informative than the successes, and four of the first six became build rules rather than reminders | Active |
| D057 | An overstated verification claim logged as a distinct failure mode | Wrong code is caught by a test; a wrong claim about what was verified reads like evidence | Active |
| D063 | Validation stays in `Money`'s compact constructor; moving it to `Money.of()` to make it mutable-by-Pitest was rejected | `Money` is a record, so the canonical constructor is public: `new Money(rawBigDecimal, INR)` would bypass scale normalisation entirely. That trades an unmeasured invariant for a bypassable one — a correctness regression, and the reason is the bypass, not that the alternative merely suited a tool | Active |
| D064 | A mutation score is bounded by what the tool elects to mutate, and `07-TEST-STRATEGY.md` §5 says so | Pitest mutates no record compact constructor unless `-FRECORD` is set; even then it attributes no killer to coverage arising from a test class's static initialiser, and `Money`'s rounding policy has no mutable construct at all. The README quotes the number, so the number must carry its limits | Active |
| D065 | Pitest runs with `-FRECORD`, accepting uncovered mutations in record-generated methods | With the filter on, `ExchangeRate` generated zero mutations and the gate reported 100% having tested none of it. Off, the real constructor guards are mutated and killed and the score is 80% against a 70% threshold; the cost is junk survivors in `equals`/`hashCode`/`toString` | Active |
| D066 | `Money`'s rounding policy verified by a recorded manual probe rather than left unverified | No default mutator applies to `setScale(currency.scale(), HALF_EVEN)` — no conditional, no operator, no return value. `HALF_EVEN`→`HALF_UP` fails 3 of 4 rounding tests and `scale()+1` fails three nested classes; both are recorded in §5 and re-run when the policy changes | Active |
| D062 | Every numeric tolerance is derived and shown before it is accepted, never asserted | Three tolerances were specified and all three were wrong: `07-TEST-STRATEGY.md` §2's one minor unit, D050's "to the minor unit", and the 3.7b wording. The pattern is the finding — a tolerance is an arithmetic claim, and stating one without deriving it produces a test that passes by being kept small | Active |
