# ADR-0006: Money as a value object; BigDecimal only

**Status:** Accepted · **Date:** 2026-09-09

## Context
Multi-country payroll data. Floating point cannot represent 0.1 exactly; adding INR to EUR is
meaningless; rounding differences in compensation are a legal problem, not a cosmetic one.

## Decision
An immutable `Money(BigDecimal amount, CurrencyCode currency)` record. `plus`/`minus` throw
`CurrencyMismatchException` across currencies. Conversion requires an explicit dated `ExchangeRate`.
Scale is fixed per currency, `RoundingMode.HALF_EVEN`. Persisted as `NUMERIC(15,2)` + `CHAR(3)`.
Serialised as a string amount so no JavaScript client rounds a large number.

## Consequences
**Good.** A whole class of currency bugs becomes a compile-time or fail-fast error. Conversions are
auditable because the rate used is explicit.

**Bad.** More verbose than a `double`; requires a JPA `@Embeddable` and Jackson serialisers; arithmetic
is method calls, not operators.

## Rejected
- *`double`* — non-negotiable no.
- *Minor units as `long`* — correct and fast, but awkward for currencies with differing exponents and
  less readable in a domain that is read far more than it is computed.
- *JavaMoney (JSR-354)* — capable but heavy, and its API is broader than this domain needs.
