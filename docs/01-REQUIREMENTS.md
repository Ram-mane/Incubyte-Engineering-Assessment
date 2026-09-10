# Requirements — CompensationIQ

> **The one-page requirements document the assessment asks for.** Scope confirmed with Incubyte
> (the Incubyte hiring team, 10 Sep 2026) — see [CLARIFICATIONS.md](CLARIFICATIONS.md) for all ten answers.
> Detail lives in [02-DOMAIN-MODEL.md](02-DOMAIN-MODEL.md) and [09-TRADEOFFS.md](09-TRADEOFFS.md).

---

## 1. Goal

Replace the spreadsheets ACME's HR team uses to manage salary data for 10,000 employees across
multiple countries with a web application that (a) manages compensation data and (b) lets the HR
Manager **answer questions about how the org pays people** through dashboards rather than exports.

Payroll — tax, payslips, deductions, disbursement — is a different product and is out of scope.

## 2. Who this is for

| Persona | Needs | In v1 |
|---|---|---|
| **HR Manager** (primary) | Full read/write on employees and salaries. Answers exec questions on pay. | ✅ |
| **HR Analyst** | Read-only. Same dashboards, cannot change pay. | ✅ |
| Employee / Manager | Self-service view of own or team pay | ❌ |
| Finance / Payroll | Disbursement, tax, payslips | ❌ |

## 3. Scope

### 3.1 Manage — the system of record
- **Employee directory** for 10,000 employees: department, job title, level, country, employment
  type, hire date, manager, status. Server-side paginated, searchable, filterable.
- **Current annual base salary** per employee, held in that employee's **native local currency**.
- **Salary revisions** — updating a salary writes an append-only audit entry (previous value, new
  value, who, when, why). This is an audit trail, not a temporal query model: the system answers
  *"what is this person paid"* and *"what changed and who changed it"*, not *"what did the org look
  like on 3 March 2024"*. See §4.
- **Salary bands** per (job title × level × country) used to *display* where a salary sits within its
  range. Enforcement rules and alerting are future scope.
- **Bulk CSV import** with row-level validation and a rejected-rows report — the customer's data is
  currently in Excel, so ingesting it is part of solving their problem.

### 3.2 Answer questions — the dashboard
Confirmed with Incubyte as the target of this build.

**KPI summary cards**
1. Total global payroll spend, normalised to a single base reporting currency (USD)
2. Active headcount
3. Average and median salary

**Interactive filters** applied across every card and chart: country, department, job title/role,
seniority level, employment status.

**Charts and tables**
4. Payroll spend broken down by department / country / level
5. Salary distribution by role or department — median, p25, p75, p90, and range
6. Headcount and average pay by dimension
7. Where each employee's salary sits within its band, shown on the employee record and as a sortable
   column in the directory

### 3.3 Non-functional
- Salaries stored in local currency; a **seeded FX rate table** normalises aggregates to USD.
- Money is `BigDecimal` + currency, never a float, never summed across currencies without conversion.
- 10,000 seeded employees. p95 < 300 ms on directory and dashboard, proven by a committed load test.
- Server-side pagination, deliberate indexing, efficient filtered search — Incubyte asked
  specifically for these to be demonstrable.
- Every mutating endpoint authenticated; writes authorised by role.

## 4. Explicitly out of scope

Decisions, not omissions. Each was confirmed with Incubyte or is a deliberate narrowing.

| Excluded | Why |
|---|---|
| **Payroll processing** — tax, payslips, deductions, disbursement | A separate regulated product per country. Confirmed out of scope. |
| **Effective-dated salary history / point-in-time snapshots** | Confirmed: current salary is sufficient. A full temporal model (validity intervals, retroactive corrections, as-of queries) would have been the largest thing in the build for value the customer said they do not need. **We keep an append-only revision log** because salary data warrants an audit trail and it costs one table — but we do not build interval arithmetic or as-of querying on top of it. [ADR-0002](adr/0002-current-salary-with-audit-log.md) records what was rejected and how it would be added. |
| **Total rewards** — bonus, equity, allowances | Confirmed out of scope. Annual base salary only. |
| **What-if / merit-increase simulation** | Confirmed deferrable. |
| **Compensation review tracking** ("who is overdue a raise") | Confirmed deferrable — it needs history to be meaningful. |
| **Band enforcement rules and alerting** | Confirmed future scope. Bands are displayed, not enforced. |
| **Approval workflows** | Confirmed out of scope. The revision log already records who made each change, so a `DRAFT → APPROVED → APPLIED` flow layers on without migration. [ADR-0009](adr/0009-defer-approval-workflow.md). |
| **Live FX feed** | Rates are seeded, behind an `ExchangeRateProvider` port. Also keeps tests deterministic. |
| **Employee self-service portal** | Different persona, different auth surface and privacy model. |
| **Pay-equity / demographic analysis** | Needs protected-attribute data, a legal review and a privacy model that do not belong in a short build. |
| **Multi-tenancy** | One customer org. A speculative unused `tenant_id` would be cargo-culting. [ADR-0010](adr/0010-single-tenant.md) describes how it would be added. |

## 5. Assumptions

- A1. One current salary per employee, annual, in their country's currency.
- A2. Bands are supplied by the org; the system does not source market data.
- A3. FX rates are seeded and dated; USD is the default reporting currency, configurable.
- A4. 10,000 employees is the functional dataset. Scale headroom is designed for and documented, not
  built — see [06-SCALABILITY.md](06-SCALABILITY.md).

## 6. Definition of done

- [ ] Every KPI card, filter and chart in §3.2 working against 10,000 seeded employees
- [ ] Seed runs in one command and is deterministic
- [ ] Suite green, fast (< 90 s), deterministic; mutation score ≥ 70% on domain packages
- [ ] ArchUnit enforces the module boundaries in [03-ARCHITECTURE.md](03-ARCHITECTURE.md)
- [ ] k6 report and `EXPLAIN` plans committed as evidence of pagination and indexing
- [ ] Deployed and publicly reachable; credentials and a 3–5 min demo video linked in the README
- [ ] Docker Compose for local fallback
- [ ] Every significant decision has an ADR
