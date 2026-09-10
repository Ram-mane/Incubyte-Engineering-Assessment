# Execution Plan — 3 days

Scope was confirmed with Incubyte on 10 Sep and is materially smaller than first planned
([CLARIFICATIONS.md](docs/CLARIFICATIONS.md)). The build drops from four days to three.

Submission target: **Saturday 12 Sep 2026 evening.** Incubyte said early submission gets
prioritised, so the plan front-loads a working, deployed, seeded system on Day 2 and spends Day 3
raising quality rather than adding features.

## Governing rules

- **Commit at every green.** ~40 commits. The history is graded; it should read like a story.
- **Test first, always.** No implementation commit without a test commit before or with it.
- **Never break main.** CI green at every push.
- **Docs are commits too.** A repo where all documentation appears in the final commit tells
  reviewers it was written to impress rather than to think.
- **Do not rebuild what was descoped.** No effective-dating, no as-of queries, no what-if, no band
  enforcement. If it feels tempting, it belongs in "what I'd do next".

---

## Day 0 (today, ~1.5 h) — set the table

| # | Task | Commit |
|---|---|---|
| 0.1 | `git init`, README skeleton, LICENSE, `.gitignore` | `chore: initialise repository` |
| 0.2 | Commit planning docs | `docs: add requirements, domain model and architecture` |
| 0.3 | Commit ADRs 0001–0011 | `docs: record initial architecture decisions` |
| 0.4 | Commit `CLAUDE.md` + `.claude/` | `chore: add AI development guardrails` |
| 0.5 | Commit the clarification answers and the superseded/replacement ADRs **as their own commit** | `docs: revise scope after customer clarification` |

> 0.5 matters. Committing the scope reversal separately, after the original design, is what makes
> the reversal legible. A reviewer running `git log --reverse` sees: thought → asked → adjusted.

---

## Day 1 — skeleton and domain core

**Morning — walking skeleton, deployed by lunch**

| # | Task | Commit |
|---|---|---|
| 1.1 | Spring Boot 3.3 / Java 21 / Maven; Spotless, Checkstyle, JaCoCo, Pitest, ArchUnit wired | `chore: scaffold spring boot application with quality gates` |
| 1.2 | Docker Compose (Postgres 16 + `pg_trgm`), Flyway, Testcontainers base | `chore: add postgres, flyway and testcontainers` |
| 1.3 | GitHub Actions CI | `ci: run build, tests and quality gates on push` |
| 1.4 | Angular 19 standalone + Material, proxy to API | `chore: scaffold angular client` |
| 1.5 | Deploy both to Render; live URL in README | `ci: deploy to render` |
| 1.6 | ArchUnit rules for the layering | `test: enforce hexagonal boundaries with archunit` |

**Afternoon — the domain, pure Java, TDD, no framework**

| # | Task | Commit |
|---|---|---|
| 1.7 | `Money` + currency-mismatch behaviour + per-currency scale | `feat: add money value object with currency safety` |
| 1.8 | `ExchangeRate` + FX normalisation, with the sum-then-convert property test | `feat: add dated exchange rates and currency normalisation` |
| 1.9 | `Employee` aggregate + typed identifiers | `feat: add employee aggregate` |
| 1.10 | **`Employee.changeSalaryTo()` → `SalaryRevision`**, invariants I1–I9 | `feat: add salary change with mandatory audit revision` |
| 1.11 | jqwik property: audit chain is contiguous and complete | `test: prove audit chain consistency with property tests` |
| 1.12 | `SalaryBand` + `BandPosition` / compa-ratio classification | `feat: add salary bands and band position` |

---

## Day 2 — make it real, end to end

| # | Task | Commit |
|---|---|---|
| 2.1 | Flyway V1: department, app_user, employee (with embedded salary) | `feat: add employee schema` |
| 2.2 | Flyway V2: `salary_revision`, insert-only grants | `feat: add append-only salary revision log` |
| 2.3 | Test asserting the DB rejects `UPDATE`/`DELETE` on revisions | `test: verify salary revisions are append-only at the database` |
| 2.4 | Flyway V3: indexes — filter, keyset, trigram, rollup | `perf: add directory and dashboard indexes` |
| 2.5 | Persistence adapters + ports | `feat: add persistence adapters for employee and revisions` |
| 2.6 | `OnboardEmployee`, `SearchEmployees` with keyset pagination + trigram search | `feat: add employee directory with keyset pagination and search` |
| 2.7 | `ChangeSalary`, `GetSalaryRevisions` | `feat: add salary change and revision log use cases` |
| 2.8 | REST controllers + RFC 7807 errors | `feat: expose employee and salary rest api` |
| 2.9 | Spring Security + JWT + method-level authorisation | `feat: add jwt authentication and role-based authorisation` |
| 2.10 | **Seed: 10,000 employees, ~30k revisions, bands, FX — under 20 s** | `feat: add deterministic seed data generator` |
| 2.11 | Angular: login, directory (virtual scroll, filters, search) | `feat: add employee directory screen` |
| 2.12 | Angular: employee detail + revision log | `feat: add employee detail with revision log` |
| 2.13 | Angular: change-salary dialog | `feat: add salary change dialog` |
| 2.14 | Redeploy; verify live end to end | `ci: deploy day two build` |
| 2.15 | **Set `failOnEmptyShould=true`** now that all five module packages exist | `test: fail architecture rules that match no classes` |

> 2.15 closes a hole opened on Day 1. Until the module packages exist, an ArchUnit rule that
> matches no classes has to pass, or the build fails on empty rules from the first commit. Once
> they exist that tolerance becomes a liability: a package rename would silently disable a rule
> and the build would stay green. Five of the eight rules match nothing today.

> **End of Day 2 there is a real, deployed, usable product with 10,000 employees in it.**

---

## Day 3 — the dashboard, then prove and polish

**Morning — the differentiator**

| # | Task | Commit |
|---|---|---|
| 3.1 | Analytics module + read-only ArchUnit rule | `feat: add read-only analytics module` |
| 3.2 | **KPI summary — spend, headcount, average, median in one query** | `feat: add dashboard kpi summary` |
| 3.3 | Breakdown by department / country / level | `feat: add payroll breakdown by dimension` |
| 3.4 | Distribution — p25/median/p75/p90 by role | `feat: add salary distribution analytics` |
| 3.5 | Band positions as a sortable column | `feat: add band position reporting` |
| 3.6 | Shared filter set applied across all dashboard endpoints | `feat: apply shared filters across dashboard` |
| 3.7 | `EXPLAIN`-asserting tests + N+1 statement-count guard | `test: assert dashboard queries use indexes` |
| 3.7b | **SQL aggregate vs a full-precision `BigDecimal` reference over the same seeded rows, asserting exact equality** — the reference mirrors the SQL: accumulate across all currencies unrounded, round once at the end. It must not be built from `Money`, which rounds on construction (D032) — `Money` represents amounts, it is not an accumulator. Zero tolerance, because "to the minor unit" is still a tolerance and the real gap would be `0.005 × currencies` ([ADR-0013](docs/adr/0013-sum-then-convert-for-currency-totals.md), D061) | `test: pin the sql total to a java reference computation` |
| 3.8 | Caffeine caching, evicted on `SalaryChanged` | `perf: cache dashboard aggregates and fx rates` |
| 3.9 | Angular dashboard: KPI cards, charts, filter bar | `feat: add dashboard with kpi cards and filters` |
| 3.10 | CSV bulk import + rejected-rows report (API + screen) | `feat: add csv bulk import with row-level validation` |

**Afternoon — evidence and finish**

| # | Task | Commit |
|---|---|---|
| 3.11 | k6 scenarios with thresholds; run and commit report + `EXPLAIN` plans | `docs: add performance report` |
| 3.12 | 100k-employee seed run for the comparison curve | `docs: add 10x scale performance comparison` |
| 3.13 | Pitest to ≥ 70% on domain; fill the gaps it finds | `test: strengthen domain tests to meet mutation threshold` |
| 3.14 | Playwright: five critical journeys | `test: add end-to-end journey specs` |
| 3.15 | Accessibility pass (axe, keyboard, focus, labels) | `fix: address accessibility issues` |
| 3.16 | Empty / loading / error states everywhere | `feat: add empty and error states` |
| 3.17 | OpenAPI + Swagger UI link | `docs: publish openapi specification` |
| 3.18 | Finalise `08-AI-WORKFLOW.md` with real prompts and rejected output | `docs: record ai workflow and rejected output` |
| 3.19 | README: live URL, credentials, doc index, video link | `docs: write project readme` |
| 3.20 | Final deploy + smoke test | `ci: deploy final build` |
| 3.21 | **Record the demo video (3–5 min)** | — |
| 3.22 | Send the repo link | — |

---

## Demo video script (3–5 min — the highest-leverage hour of the whole exercise)

Incubyte asked for 3–5 minutes. Most candidates narrate their UI. Lead with thinking.

| Time | Content |
|---|---|
| 0:00–0:30 | The problem, and the scope I confirmed before building. One line: I asked ten questions, three answers changed the design, and the repo shows the reversal. |
| 0:30–2:00 | The product: dashboard KPI cards, apply a country filter and watch everything recompute, distribution by role, then the directory at 10,000 rows with search and paging. |
| 2:00–3:00 | Change a salary → the revision appears in the audit log. Then show `Employee.changeSalaryTo()` returning the revision, and the DB grant that makes the log append-only. |
| 3:00–4:00 | Engineering evidence: ArchUnit failing on a deliberate violation, the mutation score, and the `EXPLAIN` plan showing the trigram index doing the search. |
| 4:00–4:45 | What I left out and why (the ADR-0002 reversal), and the scale path with its triggers. |
| 4:45–5:00 | `CLAUDE.md` and one rejected AI output. |

Record with OBS, one take per section. Show the terminal and the code, not only the browser.

---

## If time runs short — cut in this order

1. 100k-scale comparison run (3.12)
2. Bulk import UI (3.10) — keep the API
3. Distribution chart (3.4) — keep the KPI cards and breakdown
4. Playwright down to two specs (3.14)

**Never cut:** the audit-log guarantee and its tests, the KPI dashboard with filters, the seed
script, the deploy, the README, the ADRs, or the demo video.
