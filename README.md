# CompensationIQ

Salary management and compensation analytics for an HR Manager responsible for ~10,000 employees
across multiple countries.

> **Submission for the Incubyte Software Craftsperson assessment.**
> Built by Ram Subhas Mane · September 2026

**Live demo:** https://salary-management-ui-5bp6.onrender.com
**API:** https://salary-management-api-bv03.onrender.com · **health:** [`/actuator/health`](https://salary-management-api-bv03.onrender.com/actuator/health)

> **Deployed state:** the walking skeleton is live — Angular client, Spring Boot API and a
> Flyway-migrated Neon PostgreSQL. The domain, the directory and the dashboard land over the
> following two days. Free-tier instances spin down when idle, so the first request after a
> quiet period takes roughly 50 seconds.

**API docs:** _Swagger UI is published with the REST API._
**Demo credentials:** _issued with authentication._
**Video walkthrough (3–5 min):** _recorded against the finished build._

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
mandatory reason and an append-only audit log · salary bands per role × level × country · CSV bulk
import with a rejected-rows report.

**Answer questions** — a dashboard with KPI cards (total global payroll spend, active headcount,
average and median salary, all normalised to USD through a seeded FX table), interactive filters by
country / department / role / level that recompute every card and chart, payroll breakdown by
dimension, salary distribution with p25 / median / p75 / p90, and each employee's position within
their band.

## Running it

```bash
docker compose up -d postgres
./mvnw spring-boot:run -Dspring-boot.run.profiles=local,seed   # 10k employees, ~30k revisions
cd ui && npm install && npm start                              # http://localhost:4200
```

```bash
./mvnw verify            # tests, ArchUnit, coverage, static analysis
./mvnw -Pmutation test   # mutation testing, 70% threshold on the domain
cd ui && npm test && npm run e2e
k6 run perf/k6/directory-browse.js
```

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
Material · Testcontainers · ArchUnit · Pitest · jqwik · k6 · GitHub Actions

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
| [ADRs](docs/adr/) | Thirteen decision records, each with rejected alternatives — including one superseded and kept |
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

## Known issues and what I would do next

An independent QA pass walked the running application against the requirements and found these.
The ones that mattered for correctness or for the confirmed scope are fixed and listed in the
commit log; what follows is what it found and I chose **not** to fix before submitting, with its
framing rather than mine.

**Confusing**

- **C2 — job titles are not scoped to department.** The directory's Job title filter offers every
  title in the org regardless of the department selected, so it is possible to choose a combination
  that matches nobody. Scoping the options to the current department is a query change and a
  dependent-dropdown in the UI.
- **C3 — search normalisation.** Name search is trigram-backed and case-insensitive, but does not
  fold accents: "Muller" does not find "Müller". `unaccent` plus a functional index would fix it,
  and would need its own `EXPLAIN` evidence because it changes which index the planner picks.
- **C7 — money format in the change-pay dialog.** The current salary is shown as the raw decimal
  string (`1380000.00`) next to figures that are formatted elsewhere (`₹13,80,000`). Deliberate
  while the input was free text — the hint had to match what you would type — and now that the
  field is a number input it could be formatted.
- **C8 — sliding session expiry.** The JWT is short-lived with no refresh, so a long editing
  session can expire mid-form. The expiry is handled visibly rather than silently (you are sent to
  sign in with a reason), but the work in progress is lost.
- **C11 — table overflow at narrow width.** The directory and dashboard tables can overflow their
  container below roughly 700px. **Method caveat, which the reviewer stated:** this was observed by
  resizing a desktop browser, not confirmed on a real device, so the severity is a judgement rather
  than a measurement.

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
  against a 70% threshold; the build currently kills **178 of 201 mutations (89%)**, test strength
  96%. Tests are proven to detect defects, not merely to execute lines — and
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
- **`Clock` injected everywhere.** No `now()` in domain or application code, so audit timestamps and
  FX rate selection are fully testable and the suite is deterministic.

## What I left out, and why

Payroll processing · effective-dated history and point-in-time reporting · total rewards · what-if
simulation · review tracking · band enforcement and alerting · approval workflows · self-service ·
live FX · pay-equity analysis · multi-tenancy.

Every one is a deliberate exclusion, most of them confirmed with the customer, with the reasoning in
[the requirements](docs/01-REQUIREMENTS.md#4-explicitly-out-of-scope) and, where it changed a
decision already made, an ADR.
