---
description: Day 3 morning — the dashboard, the product's differentiator (tasks 3.1-3.10)
---

Execute **PLAN.md tasks 3.1 through 3.10**, one commit per task. Stop after 3.10.

This is what makes the submission a product rather than a CRUD app. Incubyte asked specifically for
dashboards, KPI cards and interactive filters — build those well before anything else.

**Aggregate in SQL. Always.** If you catch yourself loading employees to sum, average or group them
in Java, that is the bug this whole architecture exists to prevent.

- **3.2 the KPI summary is ONE query** — total payroll spend, active headcount, average and median,
  with FX normalisation, in a single round trip. Four cards driven by four requests is a visible
  stutter on every filter change.
- **3.3–3.5** breakdown by dimension, distribution with `percentile_cont` (p25/median/p75/p90), band
  positions as a sortable column.
- **3.6** the shared filter set — country, department, role, level, status — applied identically
  across every dashboard endpoint. One filter object, one place it is translated to SQL.
- **3.7** for each query: an integration test asserting the result **and** that `EXPLAIN` shows an
  index scan. Plus the statement-count guard. This is how indexing is demonstrated rather than
  claimed.
- **3.8** Caffeine caching, evicted on `SalaryChanged`, not TTL alone.
- **3.9** the Angular dashboard. Load the `dataviz` skill before writing any chart code.
- **3.10** CSV bulk import, streamed, with a rejected-rows report.

Run the `perf-check` skill on every query in 3.2–3.5 before committing it.

Finish with `/wrap`.
