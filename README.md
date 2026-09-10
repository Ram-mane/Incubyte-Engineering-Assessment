# CompensationIQ

Salary management and compensation analytics for an HR Manager responsible for ~10,000 employees
across multiple countries.

> **Submission for the Incubyte Software Craftsperson assessment.**
> Built by Ram Subhas Mane · September 2026

**Live demo:** _<url>_ · **API docs:** _<url>/swagger-ui.html_
**Demo credentials:** `hr.manager@acme.test` / `<password>` (read/write) · `hr.analyst@acme.test` / `<password>` (read-only)
**Video walkthrough (3–5 min):** _<url>_

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

## Engineering notes

- **ArchUnit enforces the architecture.** Framework code in the domain, a port in the wrong package,
  or a cycle between modules fails the build. The diagram and the code cannot drift.
- **PostgreSQL enforces the audit guarantee.** The application role holds `INSERT` and `SELECT` on
  `salary_revision` and nothing else, so the log is append-only independent of application code.
- **Mutation testing, not coverage theatre.** Pitest at 70% on the domain — tests are proven to
  detect defects, not merely to execute lines.
- **The dashboard aggregates in SQL.** Four KPI cards in one round trip, `percentile_cont` for the
  distributions, no entity ever loaded to be summed in Java. Integration tests assert the query
  plans use indexes — including that name search uses the trigram index rather than a sequential
  scan that only looks fast at 10,000 rows.
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
