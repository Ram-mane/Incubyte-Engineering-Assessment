# Query evidence

`EXPLAIN (ANALYZE, BUFFERS)` for every query the product runs often, captured against the seeded
dataset — 10,000 employees across six countries, 30,000 salary revisions — on PostgreSQL 16.

These are raw planner output, saved as files. A claim that an index "made the query fast" is worth
what the plan behind it is worth, so the plans are here and the conclusions below are drawn from
them rather than the other way round.

## Method

Each query was run twice against the same database: once with the relevant index present, and once
inside `BEGIN; DROP INDEX …; EXPLAIN …; ROLLBACK;`, which gives a truthful "before" without
rebuilding anything. Two queries also have a `.no-indexes` capture, where every secondary index on
`employee` is dropped in the same way — because "before this index" and "before any index" are
different questions and only the second one measures what indexing is worth.

Nothing was rewritten to suit an index. Where the planner declined an index, that is recorded as
the result.

## What the numbers say

| Query | Without | With | Plan chosen |
|---|---|---|---|
| Directory page, first 50 | 5.18 ms | **0.196 ms** | `Index Scan using ix_employee_keyset` |
| Directory page, deep keyset | 2.13 ms | **0.256 ms** | `Index Scan using ix_employee_keyset` |
| Filtered: country + department | 1.84 ms | **0.571 ms** | `Bitmap Index Scan on ix_employee_rollup` |
| Trigram search `%whitfi%` | 11.89 ms | **2.33 ms** | `Bitmap Index Scan on ix_employee_search_trgm` |
| One employee's revisions | 2.76 ms | **0.249 ms** | `Index Scan using ix_revision_employee` |
| Rollup by department × country | 4.73 ms | 5.18 ms | **`Seq Scan` — index declined** |
| Filter-options `DISTINCT department` | 3.06 ms | **1.53 ms** | `Index Only Scan using ix_employee_department` |

## Three results worth reading rather than skipping

**The keyset index is the whole argument for keyset paging.** Without it the directory page is a
`top-N heapsort` over all ten thousand rows — the sort is the cost, and it is paid identically
whether you ask for page one or page two hundred. With it the page is a range scan that stops after
fifty rows. 5.18 ms to 0.196 ms is a twenty-six-fold difference on a table small enough that
everything is already in memory; on a table that is not, it is the difference between a screen and
a spinner.

**`ix_employee_filter` was specified, measured, and not shipped.** `05-DATA-MODEL.md` called for a
covering index on `(status, department, country_code, seniority_level)`. It was created, and the
planner never chose it: with the partial rollup index available, the filtered query prefers that
one — it is narrower, it already excludes non-`ACTIVE` rows, and it costs fewer index pages to
walk. The plan with the index present and the plan without it are byte-for-byte the same shape. So
the index was removed from the migration and the data model corrected, rather than shipped to make
a document true. An index nothing chooses is write cost with no read benefit. Worth revisiting at
the hundred-thousand-row comparison, where the arithmetic changes.

**The rollup index is declined for the unfiltered aggregate, and that is correct.** Grouping every
active employee by department and country reads all ten thousand rows whatever the access path, and
a sequential scan of a 194-page table beats walking an index to reach the same rows: 4.73 ms
against 5.18 ms, the index being marginally *worse*. The index still earns its place — it is what
the filtered directory query uses — but its nominal purpose is not the one it serves. At 10,000
rows a sequential scan is frequently the right plan, and a dashboard that "proved" its index by
hiding that would be proving something else.

## Files

| File | Query |
|---|---|
| `01-directory-page.*` | unfiltered first page, ordered by the keyset sort key |
| `02-directory-page-deep.*` | page two hundred, via the row-value cursor comparison |
| `03-filtered-country-department.*` | `status + country + department`, plus a `.no-indexes` baseline |
| `04-trigram-search.*` | `ILIKE '%whitfi%'` over name and email |
| `05-revisions-by-employee.*` | one employee's audit log, newest first |
| `06-rollup-by-dimension.*` | headcount and local total by department × country, plus `.no-indexes` |
| `07-filter-options-distinct.txt` | `SELECT DISTINCT department`, which is what the V2 single-column indexes are for |

The 30,000 revisions were generated in SQL for this measurement; the seed generator writes them as
part of its own task. Everything else is exactly what `mvn spring-boot:run -Dspring-boot.run.profiles=local,seed`
produces.
