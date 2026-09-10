# Clarifications

The assessment asks candidates to clarify rather than assume. Ten questions sent to Incubyte on
**9 Sep 2026**; answered by Sandli Srivastava on **10 Sep 2026**. Every answer is reflected in
[01-REQUIREMENTS.md](01-REQUIREMENTS.md) and, where it changed a decision already made, in an ADR.

| # | Question | Answer received | Effect on the build |
|---|---|---|---|
| 1 | Compensation data management, or full payroll (tax, payslips, disbursement)? | Compensation data and reporting/analytics only. Payroll explicitly out of scope. | Confirmed the original plan. |
| 2 | Which questions must the HR Manager be able to answer? | Structured visual dashboards, KPI summary cards (total global payroll spend, active headcount, average/median salary) and interactive filters by country, department and role. What-if simulation, review tracking and point-in-time snapshots deferred. | **Changed.** Three of the seven planned analytics dropped; the dashboard and its filters became the primary deliverable. |
| 3 | Full effective-dated salary history, or is current salary sufficient? | Current salary per employee is completely sufficient. Revision logs and effective-dated history deferrable. | **Changed the data model.** Temporal design superseded — [ADR-0002](adr/0002-current-salary-with-audit-log.md), with the original kept at [ADR-0002a](adr/0002-temporal-salary-model.superseded.md). An append-only audit log was retained; the reasoning is in that ADR. |
| 4 | Base pay only, or total rewards? | Annual base salary in native currency only. Bonus, equity, allowances and deductions out of scope. | Confirmed. |
| 5 | Are salary bands part of the domain? | Displaying distributions and bands in the dashboard is good. Band *enforcement* rules and alerting are future scope. | **Narrowed.** Bands are displayed, never enforced. No alerting. |
| 6 | Angular or React, given the JD says Angular and the doc lists React first? | Java (Spring Boot) + Angular is completely acceptable and aligns with the role. | Confirmed Angular. |
| 7 | Local currency with a reporting currency, or normalised on entry? | Store in native local currency; use a simple seeded FX table to normalise org-wide totals to a single base currency (e.g. USD) for aggregates. | Confirmed exactly the planned approach. |
| 8 | Is a free-tier public URL acceptable? | Yes — Render/Railway/Vercel, seeded with the 10,000 dataset, plus a 3–5 minute demo video linked in the README. Docker Compose for local fallback is a bonus. | Confirmed; video shortened from 5–7 to 3–5 minutes. |
| 9 | Is an approval workflow in scope? | Explicitly out of scope. Deferring it and documenting how it would be layered on is the right approach. | Confirmed — [ADR-0009](adr/0009-defer-approval-workflow.md). |
| 10 | Is 10,000 steady-state, or should I design for more? | Treat 10,000 synthetic records as the primary functional dataset, and use it to demonstrate server-side pagination, database indexing, efficient search filtering and JPA query performance. | **Sharpened.** Scale headroom stays documented, not built; the emphasis moved to demonstrable pagination, indexing and query performance at 10,000 — see [05-DATA-MODEL.md](05-DATA-MODEL.md) §3–5 and the committed `EXPLAIN` plans. |

Closing instruction from Incubyte:

> *"Please ensure you capture these scope choices, data model assumptions, and intentional
> trade-offs in your one-page requirements document before diving into code."*

Done: [01-REQUIREMENTS.md](01-REQUIREMENTS.md) §3–5, [09-TRADEOFFS.md](09-TRADEOFFS.md), and
[ADR-0012](adr/0012-scope-confirmed-with-customer.md).
