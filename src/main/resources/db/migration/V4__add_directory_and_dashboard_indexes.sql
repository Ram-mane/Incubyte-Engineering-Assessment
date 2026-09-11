-- The indexes the directory and the dashboard read through.
--
-- Deliberately after the queries rather than before them: an index chosen from a schema diagram
-- guesses at a predicate, and these four are shaped by SQL that exists. Every one of them has its
-- EXPLAIN (ANALYZE, BUFFERS) recorded in docs/evidence/, before and after, against the seeded ten
-- thousand - including the ones the planner declined to use, which is a result and not a failure.
--
-- No new table, so no GRANT block; SchemaGrantsIT still checks that nothing arrived without one.

-- 05-DATA-MODEL.md specified a fifth index here, ix_employee_filter, on
-- (status, department, country_code, seniority_level) INCLUDE (the rest of the row). It is not
-- created, because it was measured and the planner would not use it: with the partial rollup
-- index below available, the filtered directory query chooses that one instead - it is narrower,
-- already excludes non-ACTIVE rows, and costs fewer index pages to walk. Both plans are in
-- docs/evidence/03-filtered-country-department.*.txt, and the doc is corrected rather than the
-- index being shipped to match it. An index nothing chooses is write cost with no read benefit.
-- Revisit at the hundred-thousand-row comparison (PLAN 3.12), where the arithmetic may change.

-- Keyset pagination reads through this: the sort key is (family_name, given_name, id) and the
-- row-value comparison the adapter issues walks it in order. Without it the page is a sort of
-- everybody, which is the cost keyset exists to avoid.
CREATE INDEX ix_employee_keyset ON employee (family_name, given_name, id);

-- Free-text search. ILIKE '%rao%' cannot use a B-tree at all: a leading wildcard has no prefix to
-- descend by. The trigram GIN indexes the same expression the query searches, which is why the
-- expression is written identically in both places - a difference of one space and this is dead
-- weight the planner never considers.
CREATE INDEX ix_employee_search_trgm
    ON employee USING gin ((given_name || ' ' || family_name || ' ' || email) gin_trgm_ops);

-- The dashboard's group-by dimensions, partial on ACTIVE because every aggregate is of the people
-- currently employed. INCLUDE carries the money so a rollup never touches the heap.
CREATE INDEX ix_employee_rollup
    ON employee (department, country_code, seniority_level)
    INCLUDE (salary_amount, salary_currency)
    WHERE status = 'ACTIVE';
