# CompensationIQ

Salary management and compensation analytics for an HR Manager responsible for ~10,000 employees
across multiple countries.

> **Submission for the Incubyte Software Craftsperson assessment.**
> Built by Ram Subhas Mane · September 2026

**Live demo:** https://salary-management-ui-5bp6.onrender.com
**API:** https://salary-management-api-bv03.onrender.com · **health:** [`/actuator/health`](https://salary-management-api-bv03.onrender.com/actuator/health)

**Sign in with** `hr.manager@acme.example` / `demo-password` (full access), or
`hr.analyst@acme.example` / `demo-password` (read-only). Demo credentials in a demo database,
published deliberately — see [Signing in](#signing-in).

> ⏳ **First request takes up to two minutes.** Both services are on Render's free tier and spin
> down when idle. Open the [health endpoint](https://salary-management-api-bv03.onrender.com/actuator/health)
> and wait for `{"status":"UP"}` before using the site, or the first screen is a spinner. Measured
> cold start: 67 s on a warm afternoon, over 120 s after a long idle.

**API contract:** [`openapi.yaml`](openapi.yaml) — hand-written, and checked against the
controllers. There is no Swagger UI; the spec is the contract and it is in the repository.

**Video walkthrough (3–5 min):** https://drive.google.com/file/d/1c5UlFRHVQilX_U-pOAByc1Wnooxd9o41/view?usp=sharing

It was recorded before the final mutation run and quotes the figures standing at the time —
178 of 201 killed, test strength 96%. The numbers in this README are the current ones: 213 of 234
(91%), strength 98%. The score went up because Java landed after the earlier run, not because
anything was re-measured to look better.

---

## The problem, and how the scope was settled

ACME's HR team manages salary data for 10,000 employees in spreadsheets. The brief asks for software
that lets the HR Manager manage that data **and answer questions about how the org pays people**.

Before writing code I sent ten clarifying questions. Three of the answers changed the design —
most significantly that current salary per employee is sufficient and effective-dated history is
deferrable. [docs/CLARIFICATIONS.md](docs/CLARIFICATIONS.md) records every question and answer;
[ADR-0002](docs/adr/0002-current-salary-with-audit-log.md) records the model that was superseded as
a result, and the original is still in the repository at
[ADR-0002a](docs/adr/0002-temporal-salary-model.superseded.md).

The rule the build is organised around:

> **Pay cannot change without an audit record.** `Employee.changeSalaryTo()` mutates the current
> salary *and returns the `SalaryRevision`* to be written in the same transaction. There is no
> setter, and the application's database role has no `UPDATE` or `DELETE` on the revision table.

## What it does

**Manage** — employee directory for 10,000 people, server-side paginated with filters and indexed
name search · current annual base salary in each employee's local currency · salary changes with a
mandatory reason and an append-only audit log · salary bands per role × level × country, displayed
on the employee record and never enforced.

*Not built:* CSV bulk import. It is in the requirements as in-scope and it is the largest thing
missing — see [Known issues](#known-issues-and-what-i-would-do-next).

**Answer questions** — a dashboard with KPI cards (total global payroll spend, active headcount,
average and median salary, all normalised to USD through a seeded FX table), interactive filters by
country / department / role / level that recompute every card together from one query, payroll
breakdown by department / country / level, and salary distribution with p25 / median / p75 / p90 by
role or department. Each employee's position within their band is on their own record.

## Running it

```bash
docker compose up -d postgres
./mvnw spring-boot:run -Dspring-boot.run.profiles=local,seed   # 10k employees, ~30k revisions
cd ui && npm install && npm start                              # http://localhost:4200
```

```bash
./mvnw verify            # unit + integration + ArchUnit + Spotless + Checkstyle + PMD + JaCoCo
./mvnw -Pmutation test   # mutation testing, 70% threshold, currently 91%
cd ui && npm test        # 106 Angular specs
cd ui && npm run build   # the production compiler: stricter templates than the test run (D161)
```

There is no end-to-end suite and no committed load test. Playwright and k6 were planned for Day 3
and were cut when the dashboard, the currency-conversion defect and the concurrency fix took the
time. The performance evidence that does exist is `EXPLAIN` output captured by hand, below.

Both builds require **JDK 21** and **Node 22**; the Maven enforcer fails fast on anything else
rather than producing bytecode for a JVM nobody verified.

## Deploying

`render.yaml` is a Render blueprint: connecting this repository creates the API (Docker, from the
multi-stage `Dockerfile`) and the client (static, built from `ui/`). PostgreSQL is **not** declared
as a Render resource — it is a managed Neon instance, per
[ADR-0003](docs/adr/0003-postgres-over-sqlite.md) and
[03-ARCHITECTURE.md](docs/03-ARCHITECTURE.md) — so the datasource arrives as three environment
variables Render prompts for on the first deploy and never stores in the repository:

```
SPRING_DATASOURCE_URL       jdbc:postgresql://<neon-host>/<database>?sslmode=require
SPRING_DATASOURCE_USERNAME
SPRING_DATASOURCE_PASSWORD
```

Flyway migrates on startup, so a fresh database provisions itself on first boot. Render polls
`/actuator/health` to decide an instance is live; only `health` is exposed, because every other
actuator endpoint is information disclosure nobody asked for.

## Stack

Java 21 · Spring Boot 3.3 · PostgreSQL 16 · Flyway · Angular 19 (standalone, signals) · Angular
Material · Testcontainers · ArchUnit · Pitest · jqwik · GitHub Actions

## Documentation

Written **before and during** the build, not afterwards — the git history shows when each landed.

| | |
|---|---|
| [Requirements](docs/01-REQUIREMENTS.md) | Goal, scope, and **what is deliberately excluded, with reasons** |
| [Domain model](docs/02-DOMAIN-MODEL.md) | Aggregates, value objects, the ten invariants |
| [Architecture](docs/03-ARCHITECTURE.md) | Modular monolith, hexagonal modules, C4 diagrams |
| [API design](docs/04-API-DESIGN.md) | Endpoints and the conventions behind them |
| [Data model](docs/05-DATA-MODEL.md) | Schema, append-only audit log, index rationale, query plans |
| [Scalability](docs/06-SCALABILITY.md) | What is built vs designed for, and the triggered path to 1M |
| [Test strategy](docs/07-TEST-STRATEGY.md) | The pyramid, property tests, mutation testing |
| [AI workflow](docs/08-AI-WORKFLOW.md) | How Claude Code was used — **including where it got things wrong** |
| [Trade-offs](docs/09-TRADEOFFS.md) | Nineteen decisions and what each one cost |
| [Security](docs/10-SECURITY.md) | Model, and the known gaps stated plainly |
| [ADRs](docs/adr/) | Fifteen numbered records, each with rejected alternatives — sixteen files, because ADR-0002's superseded original is kept beside the one that replaced it |
| [Decision log](docs/DECISIONS.md) | 161 smaller calls and their reasoning, append-only: superseded, never edited |
| [Evidence](docs/evidence/) | `EXPLAIN` plans before and after each index, the FX drift measurement, and the JPA N+1 counted both ways |
| [Clarifications](docs/CLARIFICATIONS.md) | The ten questions asked before building, the answers, and what each changed |

## Signing in

The seeded database has two accounts, one per role. They are demo credentials in a demo database
and are published here deliberately; the deployed instance signs tokens with a secret read from
its environment, and the application refuses to start without one.

| Email | Password | Role | Can |
|---|---|---|---|
| `hr.manager@acme.example` | `demo-password` | `HR_MANAGER` | read everything, change pay |
| `hr.analyst@acme.example` | `demo-password` | `HR_ANALYST` | read everything, change nothing |

`POST /api/v1/auth/login` returns a thirty-minute HS256 token. Every other endpoint requires it:
the filter chain denies by default, so a new endpoint is protected by existing rather than by
somebody remembering to protect it.

Who made a pay change is taken from the token's subject and never from the request body. A caller
who could name somebody else as the author of a change could launder one through the audit log.

## A float that nearly reached the payroll median

The KPI query in the data model specified `percentile_cont` for the median salary, which is the
obvious choice and is what most examples use. Against the seeded ten thousand it returns:

```
    cont_median     |     disc_median
--------------------+---------------------
 113930.00993500001 | 113923.942020000000
```

`percentile_cont` interpolates between two values, so PostgreSQL cannot keep it in `numeric` and
returns **`double precision`**. That trailing `...00993500001` is float error, in a payroll figure,
in a codebase whose first non-negotiable rule is that money is never a binary floating point number.

`percentile_disc` returns `numeric`, exactly, and returns an amount somebody is actually paid —
which for a *median salary* is the more truthful answer as well as the exact one: there is a real
person on 113,923.94, and nobody is on 113,930.0099350000​1.

It was caught by checking `pg_typeof` on the aggregate before writing the Java, rather than by
noticing odd digits on a dashboard later. The same rule applies to the p25/p75/p90 distributions
([D135](docs/DECISIONS.md)), and `docs/evidence/08-kpi-summary.txt` has the plans and the types.

While checking that, the same file records a second measurement: ADR-0013 prohibits converting each
amount separately, because `Money.convertTo` rounds to the currency's scale on every call. In SQL
that concern does not apply — `numeric` multiplication is exact and nothing rounds until the end —
and summing per currency then converting gives `1213179157.200437000000`, identical to converting
per row, drift exactly zero. The rule survived; the reason it exists turned out to be a Java one.

## Pagination, and the evidence it works

The directory is keyset-paged, not `OFFSET`-paged: a page is a place to resume from, given as an
opaque cursor, so the ten-thousandth row costs what the first one does and nobody is shown twice
when a colleague is hired mid-browse.

That is a claim about correctness, so here is the check. This walks the entire seeded directory
through the live API, following only the cursors the API itself returned, and compares what came
back against what exists:

```
pages walked : 200
rows returned: 10000
distinct ids : 10000
duplicates   : 0
every employee seen exactly once: True
```

`OFFSET` cannot promise the last two lines. It counts positions rather than naming a place, so a
row inserted during paging shifts everything after it — silently repeating one row and skipping
another, which on a payroll screen is a person who appears twice and a person who does not appear
at all.

## How the tests were kept honest

Coverage says a line ran. None of the following is about coverage.

- **Every gate was proved to fail before it was trusted.** The ArchUnit rules were run against a
  deliberate violation, the append-only grant against a real `UPDATE`, and the optimistic lock by
  reverting its `WHERE` clause and watching two threads both succeed — `expected: 1 but was: 2`,
  two revisions claiming the same starting salary. A gate that has never failed is decoration.
- **Mutation testing at 91%**, 213 of 234 mutations killed, test strength 98%, against a 70%
  threshold. [`docs/07-TEST-STRATEGY.md`](docs/07-TEST-STRATEGY.md) §5 also says what that number
  *cannot* see — null guards, `Money`'s arithmetic and its rounding policy are covered by example
  tests, not by the score, and the manual probes that stand in for it are written down.
- **Tests that could not fail were found and fixed.** A median asserted where the mean would have
  passed either way; a float-artefact check that ran after `Money` had already rounded; a
  concurrency test asserting the scheduler rather than the invariant; a reload assertion that never
  looked at what was reloaded. Each is recorded in [`docs/DECISIONS.md`](docs/DECISIONS.md) with
  what a passing suite was therefore not telling me.
- **Statement counts are real.** `support/CountingDataSource` wraps the application's pool and
  counts every `execute` at the JDBC boundary; integration tests declare a number and fail above
  it. The JPA read path is measured both ways in
  [`docs/evidence/09-jpa-read-path.txt`](docs/evidence/09-jpa-read-path.txt) — 31 statements naive,
  1 with an entity graph, and 2 when a fixture accidentally hid the problem behind Hibernate's
  first-level cache.

## Known issues and what I would do next

An independent QA pass walked the running application against the requirements and found these.
The ones that mattered for correctness or for the confirmed scope are fixed and listed in the
commit log; what follows is what it found and I chose **not** to fix before submitting, with its
framing rather than mine.

**Not built**

- **CSV bulk import with a rejected-rows report.** Not built, and **not a customer requirement** —
  the customer never raised it. It entered this build's scope through
  [`01-REQUIREMENTS.md`](docs/01-REQUIREMENTS.md) §3.1, where I inferred it from their data being in
  Excel today. An inference of mine is not a request of theirs, and an earlier draft of this section
  described it as confirmed with the customer, which it never was. It stays listed because it is a
  real thing a migrating HR team would need, and because a scope item I invented is worth showing:
  streamed and chunked, partial success by default, so twelve bad rows are fixed rather than ten
  thousand re-uploaded.
- **End-to-end journeys (Playwright) and a committed load test (k6).** Both planned for Day 3 and
  both cut. What replaced them is narrower and real: the deployed site was walked through a
  headless browser to verify each finished feature, and `docs/evidence/` holds `EXPLAIN` plans
  captured with and without each index.
- **Band position as a sortable column in the directory.** The band is on the employee record; the
  directory-wide view of who sits where is not built.

**Confusing**

- **C2 — job titles are not scoped to department.** The directory's Job title filter offers every
  title in the org regardless of the department selected, so it is possible to choose a combination
  that matches nobody. Scoping the options to the current department is a query change and a
  dependent-dropdown in the UI.
- **C3 — search normalisation.** Name search is trigram-backed and case-insensitive, but does not
  fold accents: "Muller" does not find "Müller". `unaccent` plus a functional index would fix it,
  and would need its own `EXPLAIN` evidence because it changes which index the planner picks.
- **C7 — money format in the change-pay dialog.** The current salary is shown as the raw decimal
  string (`1380000.00`) next to figures that are formatted elsewhere (`₹13,80,000`). It is
  deliberate, and it stays that way: the field beside it is a **text** input, not a number input.
  `type="number"` was tried and reverted (D156) because Angular binds it through `parseFloat` —
  money through a double, which this codebase forbids on the wire — and because typing the `.` in
  `1500000.` made the browser report an empty value and cleared the field mid-entry. The hint has
  to match what you would actually type into a text field, so formatting it would make the example
  wrong rather than nicer. An earlier version of this entry said the field was now a number input
  and the figure could therefore be formatted; both halves of that were stale.
- **C8 — sliding session expiry.** The JWT is short-lived with no refresh, so a long editing
  session can expire mid-form. The expiry is handled visibly rather than silently (you are sent to
  sign in with a reason), but the work in progress is lost.
- **C11 — table overflow at narrow width.** The directory and dashboard tables can overflow their
  container below roughly 700px. **Method caveat, which the reviewer stated:** this was observed by
  resizing a desktop browser, not confirmed on a real device, so the severity is a judgement rather
  than a measurement.

**Second pass (R1–R12)**

A later walk-through of the finished build found these. The two that could mislead someone about
pay — a band verdict left on screen from the previous salary, and wire constants shown as
`MARKET_ADJUSTMENT` — are fixed and in the commit log. The rest are recorded, not fixed:

- **R1 — "role" and "job title" name the same thing.** The directory filter says Job title, the
  band copy says role, and the API field is `jobTitle`. One word should win, and it should be the
  one on the wire.
- **R2 — SGD renders as an ISO code where other currencies render as symbols.** `Intl` has no
  glyph for it and falls back to the code, so a Singapore row reads `SGD 195,000` beside
  `₹15,00,000`. Consistent by rule, inconsistent to look at.
- **R3 — the distribution headers mix registers.** Column labels shift between sentence case and
  the statistical shorthand (`p25`, `Median`, `p75`).
- **R4 — the mini bars in the distribution table are unexplained.** They encode where the median
  sits in the row's range; nothing on screen says so and there is no legend.
- **R5 — seeded pay changes are all timestamped 5:30 AM.** They are written at midnight UTC
  (`atStartOfDay().toInstant(ZoneOffset.UTC)`) and rendered in the viewer's zone, which in IST is
  05:30. Correct, and it reads as a machine rather than a person having made the change.
- **R6 — signing in does not return you to the page you asked for.** A deep link while signed out
  sends you to sign-in and then to the dashboard, losing the URL you wanted.
- **R7 — the band disclaimer is worded two ways.** The employee page says pay changes "are not
  restricted to it"; the dialog says "For context. Pay changes are not restricted to the band."
  Same rule, two sentences, and a reader has to check they mean the same thing.
- **R8 — the directory has no "Showing 0 of 0" counter on an empty result.** The count that
  frames every other result disappears in the one case where the number is the answer.
- **R9 — the "no band" copy says "role and level" when the band is keyed on role, level and
  country.** The three-part key is what the query uses and what the populated panel says; the empty
  case names two thirds of it, which would send someone looking for a band that was never missing.
- **R10 — a 21-digit amount is refused with a message about the wrong thing.** `salary_amount` is
  `numeric(19, 4)`, so anything over fifteen integer digits is rejected by PostgreSQL rather than by
  a rule the screen can explain.
- **R11 — no upper bound on a salary.** The domain requires a positive amount and nothing more, so
  the effective ceiling is R10's column width. A plausibility bound belongs in the domain, where it
  can say what it is.
- **R12 — a server-side refusal does not mark the field invalid.** The message appears and is
  announced (`role="alert"`), but the input keeps its valid styling, so the error and the field it
  is about do not look connected.

**Polish (P1–P7)**

- **P1** — no favicon; the browser tab shows the default.
- **P2** — the sign-in screen has no product name or explanation above the form.
- **P3** — dashboard KPI cards do not indicate a loading state on filter change; the figures simply
  update when they arrive.
- **P4** — the directory's Clear button is always enabled, including when nothing is filtered.
- **P5** — no page title beyond the route title; the browser history reads as a list of the same
  words.
- **P6** — the pay-history table has no column sorting.
- **P7** — ~~empty-result wording says "filters" even when only the search box was used~~ — fixed.

**Not reproducible.** The pass listed two findings it could not reproduce (N1, N2) and explicitly
marked them as such. I have not opened work for either. The one 30-second check N2 asked for — that
the "Changed by" column renders for both roles — is confirmed on the deployed build.

## Engineering notes

- **ArchUnit enforces the architecture.** Framework code in the domain, a port in the wrong package,
  or a cycle between modules fails the build. The diagram and the code cannot drift.
- **PostgreSQL enforces the audit guarantee.** The application role holds `INSERT` and `SELECT` on
  `salary_revision` and nothing else, so the log is append-only independent of application code.
- **Mutation testing, not coverage theatre.** Pitest over `shared`, `domain` and `application`
  against a 70% threshold; the build currently kills **213 of 234 mutations (91%)**, test strength
  98%. Tests are proven to detect defects, not merely to execute lines — and
  [what the score cannot see](docs/07-TEST-STRATEGY.md) is written down beside it, because a
  mutation score is bounded by what the tool elects to mutate.
- **The dashboard aggregates in SQL.** Four KPI cards in one round trip, `percentile_disc` for the
  median (`_cont` interpolates, so PostgreSQL leaves `numeric` for `double precision` — see above),
  no entity ever loaded to be summed in Java. The query plans are captured by hand and committed in
  [`docs/evidence/`](docs/evidence/) — each one run with and without its index, so the trigram
  search can be seen beating the sequential scan that only looks fast at 10,000 rows. They are
  evidence, not a gate: **no test asserts a plan yet** — that is PLAN 3.7, and until it lands
  nothing in the build would catch a regression to a sequential scan.
- **Keyset pagination throughout.** Constant-time at any depth, stable under concurrent writes.
- **Time enters as a value, not as a clock.** `changeSalaryTo` takes the `Instant` to record, so a
  domain method is a pure function of its arguments — no `Clock` reaches an aggregate. The
  application layer injects one and resolves `instant()` at the boundary, which is the one place a
  clock belongs. Audit timestamps and FX rate selection are fully testable and the suite is
  deterministic.

## What I left out, and why

Payroll processing · effective-dated history and point-in-time reporting · total rewards · what-if
simulation · review tracking · band enforcement and alerting · approval workflows · self-service ·
live FX · pay-equity analysis · multi-tenancy.

Every one is a deliberate exclusion, most of them confirmed with the customer, with the reasoning in
[the requirements](docs/01-REQUIREMENTS.md#4-explicitly-out-of-scope) and, where it changed a
decision already made, an ADR.
