# ADR-0015: A missing exchange rate refuses the answer, it does not default to 1

**Status:** Accepted · **Date:** 2026-09-12

## Context

Every dashboard query normalises salaries to a reporting currency through the seeded `exchange_rate`
table. All three analytics adapters did it with the same line:

```sql
e.salary_amount * COALESCE(fx.rate, 1) AS reporting_amount
```

That `COALESCE` answers two different questions with the same number:

1. *This salary is already in the reporting currency.* Rate 1 is correct, and it has to be a case
   rather than a lookup, because `V5`'s `exchange_rate_crosses_currencies` CHECK forbids a
   `USD -> USD` row from ever existing.
2. *We have no rate for this currency.* Rate 1 is a lie.

The adapter could not tell them apart, so it chose the wrong answer for the second. Against the
seeded data, `GET /dashboard/breakdown?groupBy=country&currency=EUR` reported India as the
organisation's largest payroll cost at **EUR 4.93bn** — 1,000,000 INR counted as 1,000,000 EUR —
with `ratesAsOf: null` and no error anywhere. `sum()` also skips NULLs, so the alternative failure
mode is a total that silently omits every unconvertible salary.

The same defect was reachable on the **default USD path**. The rates CTE pinned every currency to
one `max(as_of)`, so an FX feed refreshing only the currencies that moved would advance the snapshot
date and orphan the rest. Measured, in a rolled-back transaction against the seeded database: adding
one `EUR -> USD` row dated a month later moved reported global payroll from 1.21bn to **6.19bn**,
inverted the country ranking, and reported `ratesAsOf` as the new date — a date it had used for one
currency out of six.

## Decision

**An answer that cannot be fully converted is refused, not approximated.**

- Identity is a `CASE`, not a lookup: `WHEN e.salary_currency = :reportingCurrency THEN e.salary_amount`.
- An absent rate leaves NULL. It is never coalesced to 1.
- Every analytics query selects the currencies it could not convert, and the adapter throws
  `UnconvertibleSalaries` if that set is non-empty. The web layer maps it to **422**, naming the
  currencies and the date but never an amount (D080).
- **One snapshot date for the whole answer stands** — `V5` chose it so that this week's total and
  next week's are comparable statements rather than coincidences. The consequence is that a
  partially refreshed rate table is now a loud refusal instead of a silent six-fold error.
- The shared CTE lives in one place (`NormalisedSalaries`), because the copy was the defect: one
  wrong line had to be found and fixed three times.

## Consequences

**Good.** No dashboard figure can be wrong by a factor of the exchange rate. The failure is visible,
names what is missing, and tells an operator what to fix. The refusal is all-or-nothing, so a total
can never quietly omit a currency.

**Bad, and sharper than it first looks.** A rate table that is incomplete takes the whole dashboard
down rather than degrading — and **an operator cannot fix it from the running system**. `V5` grants
the application `SELECT` only on `exchange_rate` and seeds the rows from a migration, so restoring
the dashboard means a migration and a deploy. One employee onboarded outside the six seeded
countries — `CountryCode` admits every ISO country the JDK has a currency for — is enough to do it,
on the default unfiltered view, for every user. The message names what is missing; it does not put
it within reach. Until rates come from the `ExchangeRateProvider` port with a writable store behind
it, that gap is real and is the price of never printing a wrong figure.

A rate table that is incomplete takes the whole dashboard down rather than degrading. For
payroll figures that is the right trade — a number an HR manager would act on and could not
question is worse than a page that says why it will not answer — but it does mean rate coverage is
now an availability dependency. The reporting currency is still not validated at the edge: asking
for a currency the table cannot reach is a 422 from the query rather than a 400 from the request.

**Trigger to revisit.** If rates ever come from a live feed rather than a seed, the single-snapshot
rule needs revisiting with it: a feed that updates per currency cannot satisfy "every currency on
one date", and the choice then is per-currency latest rates plus a reported date range.

## Rejected

- *Keep `COALESCE(fx.rate, 1)` and validate the reporting currency at the controller.* Closes the
  `?currency=` door and leaves the default path open: the partial-refresh case needs no query
  parameter at all.
- *Latest rate per currency (`DISTINCT ON`).* Fixes the partial refresh by mixing dates, which is
  precisely what `V5` decided against. It would make two aggregates computed a week apart
  incomparable in a way nobody could see.
- *Report NULL/absent figures for unconvertible groups.* A dashboard with holes in it invites the
  reader to sum what is left.
