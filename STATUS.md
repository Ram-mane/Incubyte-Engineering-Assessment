# Status

Running notes for work that is owed but deliberately not done yet. Anything here is a decision
that has been made, not a reminder to decide.

## Owed tests

| Owed by | Test | Why it is not here yet |
|---|---|---|
| 1.10 | `changing_salary_to_zero_is_rejected` | Invariant I1 moved off `Money` and onto `Employee.changeSalaryTo`. `Money` permits zero — an empty dashboard filter sums to zero, and an employee below their band minimum sits a negative distance from it. The rule belongs where a salary exists, plus a database CHECK. |

## Deferred API

| Owed by | Item | Why |
|---|---|---|
| 1.8 | `Money.convertTo(CurrencyCode, ExchangeRate)` | Conversion needs the dated `ExchangeRate`, so it lands whole with FX normalisation rather than half-built here. |

## Owed API

| Owed by | Item | Constraint |
|---|---|---|
| 1.10 | `Money.isPositive()` | The narrowest method invariant I1 needs. Not a general comparator - `isGreaterThan` was written during 1.7 and deleted unused. No comparison method without a test that requires it. |

## On probation

| Item | Condition |
|---|---|
| `Money.convertTo(CurrencyCode, ExchangeRate)` | Has no production caller. Aggregates normalise in SQL, so the only callers today are its own tests. **If it still has none at the end of Day 3, delete it** — the same standard that removed `isGreaterThan` during 1.7. Kept for now because single-amount display conversion is a real need the API will have. |
