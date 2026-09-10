---
name: perf-check
description: Verify a query or endpoint actually performs, using EXPLAIN and k6. Use after adding any query, any list endpoint, or any analytics feature, and whenever a change touches the employee or salary_revision tables.
---

# Performance check

Claims about performance are worthless; measurements are the deliverable.

## 1. EXPLAIN before you trust it
```sql
EXPLAIN (ANALYZE, BUFFERS) <the query, with realistic parameters>;
```
Against the **seeded** dataset (10,000 employees / ~30k revisions), never against five test rows.

Fail the change if you see:
- `Seq Scan` on `employee` or `salary_revision` in a query that filters
- a name search not using `ix_employee_search_trgm` — at 10k a seq scan is fast enough to hide this
- rows removed by filter ≫ rows returned (the index is not selective)
- a sort that could have been served by an index
- nested loop over thousands of outer rows

Commit the plan to `docs/perf/`.

## 2. Statement count
Every integration test for a list or detail endpoint declares its expected query count. If a change
adds a query, the test fails and you must justify it. This is how N+1 is caught automatically instead
of in production.

## 3. k6
```bash
k6 run perf/k6/directory-browse.js
```
Thresholds are in the script and the run **fails** if missed:
directory p95 < 150 ms · dashboard warm p95 < 80 ms · dashboard cold p95 < 500 ms · write p95 < 200 ms.

## 4. The rules that prevent most problems
- Aggregate in SQL. If you are looping in Java to sum, group or average, the query is wrong.
- Return projections, not entities, on read paths. No persistence context, no dirty checking.
- Keyset pagination. Never `OFFSET`.
- Batch writes. `rewriteBatchedStatements=true`, sensible batch size, no per-row round trips.
- Stream large inputs. A 10,000-row CSV must never become 10,000 objects in memory.
- Cache only what is small and hot, and evict on the domain event, not just on TTL.

## 5. When something is slow
Measure first. Do not add a cache to hide a missing index — a cache over a bad query is a bad query
with a delay. Order of attack: index → query shape → projection instead of entity → cache → then,
and only then, architecture.
