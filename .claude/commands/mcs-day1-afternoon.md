---
description: Day 1 afternoon — the pure-Java domain, TDD, no framework (tasks 1.7-1.12)
---

Execute **PLAN.md tasks 1.7 through 1.12**, one commit per task. Stop after 1.12.

This is the only genuinely interesting code in the submission and the part the interview will open
on. Follow the `tdd-cycle` skill strictly: **write the tests first, show them to me, wait for my
approval, then implement.** Never both in one step.

Zero Spring, zero JPA, zero Jackson in these classes. Whole suite must run in under two seconds.

**1.7 `Money`** — **done**, commits `fc3074a` and `9c3e0f4`. Record of `BigDecimal amount` +
`CurrencyCode currency`; same-currency arithmetic; cross-currency `plus`/`minus` throws
`CurrencyMismatchException`; scale per currency from the JDK; `HALF_EVEN` asserted at a `.005`
boundary; `100.00 INR` equals `100.0 INR` and not `100.00 USD`.

> Two instructions in the original brief for 1.7 were reversed and must not be reinstated:
> **zero and negative are legitimate `Money`** and `minus` is total (D027, D028 — the positivity
> rule is about salaries and lives on `Employee.changeSalaryTo` plus a DB CHECK), and **`convertTo`
> belongs to 1.8**, not 1.7 (D033).

**1.8 `ExchangeRate` + FX normalisation** — **done**. Dated rates, no default currency anywhere,
no Java-side rate lookup (D035 — normalisation is SQL-side; date resolution stays behind the
`ExchangeRateProvider` port for 2.x).

> The "within one minor unit" property bound was arithmetically false and is not to be
> reinstated (D060). Two properties replace it: **P1 exact, zero tolerance** — sum within a
> currency, convert once, invariant under reordering, equal to a full-precision reference rounded
> once; and **P2 bounded** at `0.005 × (n + currencies)`, named
> `per_row_conversion_accumulates_error_and_must_not_be_used_for_totals` because it documents a
> prohibition rather than permitting the drift. See [ADR-0013](../../docs/adr/0013-sum-then-convert-for-currency-totals.md).

**1.9 `Employee`** — aggregate plus typed identifiers (`EmployeeNumber`, `EmailAddress`,
`CountryCode`), validated in compact constructors.

**1.10 `Employee.changeSalaryTo(Money, ChangeReason, UserId)`** — the heart of it. Updates
`currentSalary` **and returns** the `SalaryRevision`. **No setter for `currentSalary`.** Cover
invariants I1–I9 from `docs/02-DOMAIN-MODEL.md`, one test each, and specifically:
- the revision's `previousAmount` is the value **before** the change, not after
- an identical amount and currency is rejected as a no-op (I6)
- a currency not matching the employee's country is rejected (I9)
- a terminated employee rejects any change (I8)
Inject `Clock`. No `Instant.now()` anywhere.

**1.11** jqwik property: across any sequence of accepted changes, the revision count equals the
change count and each revision's `previousAmount` equals the preceding revision's `newAmount`.

**1.12 `SalaryBand` + `BandPosition`** — compa-ratio and its classification. `Optional` when no band
matches, never `0` or `-1`. **Displayed, never enforced** — no alerting, no blocking.

Run `/review` before committing 1.10. Run the `test-auditor` subagent after 1.12.

Finish with `/wrap`.
