# ADR-0013: Totals are summed per currency and converted once, never converted per row

**Status:** Accepted · **Date:** 2026-09-10

## Context
The dashboard's headline figure is total payroll spend across six local currencies, normalised to a
single reporting currency. Two orderings are available and they do not produce the same number:

- **per-row** — convert each salary to the reporting currency, then add up the results
- **sum-then-convert** — add up each currency's salaries, convert each subtotal once, then add those

Every conversion rounds to the reporting currency's minor unit, so each one can be wrong by half a
unit. Per-row conversion rounds `n` times for `n` employees; sum-then-convert rounds once per
currency. At 10,000 employees in six currencies the two answers can differ by tens of units.

The forcing constraint is where the number is actually computed. `CLAUDE.md` requires aggregation in
SQL, and PostgreSQL's `sum(amount * rate)` over `numeric` is exact throughout and rounds **once**, at
the end. That makes the SQL result the definition of correct, not merely one candidate answer. Any
Java-side total — a test fixture, a cache warm-up, a CSV export — that rounds per row will disagree
with the figure the dashboard shows, and the disagreement grows with headcount.

`docs/07-TEST-STRATEGY.md` §2 previously specified the invariant as "converting each to USD and
summing equals summing per-currency and then converting — **within one minor unit**". That bound is
arithmetically false: the gap is bounded by `0.005 × (n + currencies)`, so a one-unit tolerance holds
only for lists short enough not to expose it. Writing a property to that sentence would produce a
test that passes because it was kept small, which is worse than no test.

## Decision
- **Sum-then-convert is the only sanctioned way to build a cross-currency total.** Sum within a
  currency at full precision, convert each subtotal once, then add the converted subtotals.
- **`Money.convertTo` remains a single-amount operation.** It is correct for converting one salary
  for display; it is not a building block for a total.
- Two properties, not one, guard this:
  - **P1, exact, zero tolerance.** Summing same-currency `Money` and converting once is invariant
    under reordering the list, and equals a full-precision `BigDecimal` reference rounded once.
  - **P2, bounded.** Per-row conversion then summing drifts from P1 by at most
    `0.005 × (n + currencies)`. It is named
    `per_row_conversion_accumulates_error_and_must_not_be_used_for_totals`.
- **P2 documents a prohibition; it does not license the drift.** Its tolerance is the measured size
  of a known defect, not a budget any caller may spend. A tolerance that grows with `n` would
  otherwise read as blessing per-row conversion, which is the exact implementation this ADR forbids.
- `07-TEST-STRATEGY.md` §2 is corrected to state the real bound.
- `PLAN.md` 3.7b adds an integration test asserting the SQL aggregate equals a full-precision
  `BigDecimal` reference over the same seeded rows **exactly**. The reference mirrors the SQL:
  accumulate across all currencies unrounded, round once at the end. It is not built from `Money`,
  which rounds on construction (D032) — `Money` represents an amount, it is not an accumulator.
  "Agree to the minor unit" was rejected as still a tolerance: the real gap there would be
  `0.005 × currencies`, three cents at six currencies. Until that test exists, the agreement between
  SQL and Java is reasoned, not demonstrated.

## Consequences
**Good.** The number on the dashboard is reproducible by hand, and the two implementations that
compute it are pinned to each other by a test rather than by intent. P1 is exact, so it fails on any
regression rather than absorbing it into a tolerance. Naming the prohibition in P2 means a future
contributor who reaches for per-row conversion meets a test that explains why not.

**Bad.** Callers cannot simply map-and-sum, which is the shape most people reach for first;
grouping by currency is more code at every call site. Sum-then-convert also needs the raw
same-currency subtotal, so an API returning only converted amounts is unusable for totals — a
constraint on the analytics projections in Day 3. And P2's tolerance is a number that must be
re-derived if the reporting currency's scale ever changes.

**Trigger to revisit.** If PLAN.md 3.7b shows SQL and the reference disagreeing at all, the cause is
a rounding policy mismatch rather than an ordering one, and this ADR is insufficient: the fix would
be to pin PostgreSQL's rounding explicitly rather than to assume `HALF_EVEN` agreement.

## Rejected
- **One property with a tolerance of `0.005 × (n + currencies)`.** The bound is right and the test
  would pass, but a single property with a tolerance growing in `n` cannot distinguish "these two
  correct paths agree" from "per-row conversion is an acceptable way to total". It would have stayed
  green over the implementation that causes the drift.
- **Keeping the documented one-minor-unit bound and capping generated lists at two elements.** This
  is writing the test to fit the sentence. jqwik would find the counterexample the moment anyone
  raised the size, and the sentence would still be wrong.
- **Rounding per row but to more decimal places than the reporting currency.** Reduces the drift
  without removing it, and introduces amounts that are not expressible in the currency they claim to
  be in — which is the problem `Money` exists to prevent.
- **Computing the total in Java over all 10,000 rows.** Removes the disagreement by removing one of
  the two implementations, and violates the aggregate-in-SQL rule for a figure that must recompute
  on every filter change.
