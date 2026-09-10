# Scalability

The brief says 10,000 employees. This document exists to show that the design is *not accidentally*
limited to 10,000 — and to be honest about what has been built versus what has been designed for.

**Built now:** everything in §2 and §3.
**Designed for, deliberately not built:** everything in §5, with the trigger for each.

Building §5 today would be over-engineering. Not knowing §5 would be under-thinking. The distinction
is the point.

## 1. What "1 million" actually means here

| | 10k employees (today) | 1M employees (100×) |
|---|---|---|
| Employee rows | 10,000 | 1,000,000 |
| Salary revisions (audit log) | ~30,000 | ~3,000,000 |
| Concurrent HR users | ~5 | ~500 |
| Peak req/s | ~10 | ~1,000 |
| Data volume | ~25 MB | ~2.5 GB |

Note the shape of this problem: **it is read-heavy and write-light.** Salaries change a few times a
year per person; the dashboard is refreshed constantly. 3M rows and 2.5 GB is a *small* database — a
single well-indexed PostgreSQL instance handles it. The scaling challenge is concurrent analytical
reads, not storage or write throughput. That observation determines every choice below.

## 2. Efficiency built in from commit one

| Technique | Where | Why it matters at scale |
|---|---|---|
| **Keyset pagination** | all collection endpoints | O(1) per page instead of O(offset). At page 20,000 of a 1M directory, OFFSET is unusable. |
| **Covering index on the directory filter** | `ix_employee_filter` | the most-used screen is an index-only scan; no heap fetch for a page of 50 |
| **Trigram index for name search** | `ix_employee_search_trgm` | `ILIKE '%x%'` cannot use a B-tree; at 10k a seq scan hides the bug, at 1M it does not |
| **Aggregation in SQL** | all `/dashboard` endpoints | 1M rows summed by PostgreSQL, never loaded into the JVM |
| **Projection DTOs, not entities, on reads** | analytics module | no Hibernate persistence context, no dirty checking, ~10× less garbage |
| **Streaming CSV import** | bulkimport | constant memory regardless of file size |
| **JDBC batch writes** | seeder, importer | 10k inserts in one round-trip batch, not 10k round trips |
| **`BigDecimal` + DB `NUMERIC`** | everywhere money exists | correctness, which does not become negotiable at scale |
| **No N+1, ever** | enforced by test | a `HibernateStatementCountAssertion` in integration tests fails the build if a request issues more queries than declared |
| **Statement + HTTP timeouts** | config | one pathological query cannot exhaust the pool |
| **Stateless JWT auth** | identity | any instance serves any request; horizontal scaling needs no sticky sessions |

That last row matters: the application is **stateless by construction**, so step 1 of scaling out is
"run more copies", with no refactor.

## 3. Caching

Caffeine, in-process, three caches with different rationales:

| Cache | Size | TTL | Why |
|---|---|---|---|
| `bands` | ~200 entries | 1 h, evicted on `BandRevised` | tiny, read on every compa-ratio calculation |
| `fxRates` | ~2,000 | 24 h | immutable once dated |
| `dashboard` | 100 | 5 min, evicted on `SalaryChanged` | expensive aggregates, tolerant of slight staleness |

Event-driven eviction rather than pure TTL, so a salary change is reflected immediately.

## 4. Proving it, not claiming it

`perf/k6/` contains three scenarios run against the seeded 10,000-employee dataset:

1. **Directory browse** — 50 VUs, keyset paging with filters
2. **Dashboard** — 20 VUs hitting the KPI cards and every chart, with filters applied
3. **Mixed write** — 5 VUs changing salaries while 45 read

Budgets, enforced as k6 thresholds so the run fails if missed:

| Endpoint class | p95 | p99 |
|---|---|---|
| Directory / detail | 150 ms | 400 ms |
| Dashboard (cold cache) | 500 ms | 1200 ms |
| Dashboard (warm) | 80 ms | 200 ms |
| Salary change write | 200 ms | 500 ms |

Results and `EXPLAIN ANALYZE` plans committed to `docs/perf/`. A second run against a
**100,000-employee** seed (10× the brief) is included to show the curve is flat, not to claim
production readiness at 1M.

## 5. The path to 1M — designed, not built

Ordered by when each becomes necessary. Each row names its **trigger**, so the decision is a
measurement, not a guess.

| Step | Trigger | Change | Cost of deferring |
|---|---|---|---|
| 1. Horizontal app scale | CPU > 60% sustained | Run N instances behind a load balancer. **Zero code change** — already stateless. | none |
| 2. Read replicas | read p95 degrades under dashboard load | Route `/dashboard` to a replica via a second `DataSource` and a `@ReadOnly` routing annotation. The analytics module is already read-only and isolated, so this is a config change. | none |
| 3. Shared cache | > 1 instance and cache-miss storms appear | Swap Caffeine for Redis behind the existing Spring `CacheManager` abstraction. One bean. | none — the abstraction is already there |
| 4. Materialised rollup tables | dashboard p95 > budget even warm | Nightly + event-driven refresh of `mv_dept_rollup`. The analytics module reads projections already, so only the SQL changes. | none |
| 5. Async import & heavy jobs | imports > 100k rows or blocking a request thread | Transactional outbox table + a `@Scheduled` poller, then a real worker process. `SalaryChanged` already flows through a publisher interface. | none — port exists |
| 6. Partition `salary_revision` | > 50M rows or vacuum pain | `PARTITION BY RANGE (changed_at)` yearly. The audit log is append-only and queried by employee and recency, so pruning works without rewriting queries. | rising migration cost — hence noted early |
| 7. Split a module into a service | one module needs independent scaling or a separate team owns it | `analytics` goes first: read-only, no shared writes, already boundary-enforced. | low, *because* boundaries are enforced by ArchUnit today |
| 8. Multi-region | non-Indian users see > 200 ms RTT | Read replicas per region; writes stay primary-region. | high — flagged as the genuinely hard one |

**What I would not do:** introduce Kafka, CQRS with separate write/read stores, or microservices at
this size. Each adds a failure mode and an on-call burden to solve a problem this workload does not
have. Steps 1–4 alone comfortably carry this system to 1M employees.

## 6. Honest limitations of the current build

- Single database instance — a failover story is configuration on a managed Postgres, not designed here.
- Bulk import is synchronous above a threshold; it will hold a thread on a very large file.
- No rate limiting; would be needed before public exposure beyond an internal HR team.
- FX rates are seeded, not live.
- The free deployment tier cold-starts. Real capacity numbers come from the local k6 runs, and the
  report says so rather than quoting misleading cloud numbers.
