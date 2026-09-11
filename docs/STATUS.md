# Session status

> Written at the end of each session by `/wrap`, read at the start of each by `/prime`.
> Hand-written, so `git log` wins any disagreement.

**Last updated:** 2026-09-10, end of Day 1 afternoon session

## Position
Day 1, task 1.8 complete — `Money`, `ExchangeRate` and `Money.convertTo`, with the two
normalisation properties. 1.9 (`Employee` aggregate) not started.

## Landed today
- `feat: add money value object with currency safety`
- `test: include shared package in mutation testing scope`
- `docs: record an overstated verification claim in the ai workflow log`
- `docs: schedule failing architecture rules that match no classes`
- `test: enforce framework-freedom across shared as well as domain`
- `feat: add dated exchange rates and currency normalisation`
- `test: mutate record compact constructors`
- `docs: add decision log and session protocol commands`

## Uncommitted
_(none)_

## Blockers
_(none)_

## Decisions recorded
D058–D064, plus [ADR-0013](adr/0013-sum-then-convert-for-currency-totals.md). D046 corrected
during backfill (five rules match zero classes, not six). D050 superseded by D061.

## Next action
PLAN.md task 1.9 — `Employee` aggregate plus typed identifiers (`EmployeeNumber`, `EmailAddress`,
`CountryCode`), validated in compact constructors. Tests first.

## Gotchas
- The root `STATUS.md` collision is resolved: that file is deleted, and its one unique item is
  now D072. This file is the only status file.
- **The mutation score does not cover everything.** Pitest mutates no record compact constructor
  unless `-FRECORD` is set, and even then attributes no killer to mutations covered from a test
  class's static initialiser. `Money`'s rounding policy has no mutable construct at all and is
  verified by a manual probe recorded in `07-TEST-STRATEGY.md` §5. Re-run that probe after any
  change to `Money`'s constructor.
- Builds need `JAVA_HOME=~/.jdks/jdk-21` (Temurin, installed by hand — the system JDK is 25 and
  the enforcer rejects it). Node 22 is at `~/.nodejs/current/bin`.
- `mcs-day1-afternoon.md` was corrected on install: its 1.7 and 1.8 briefs contained instructions
  reversed by D027, D028, D033, D035 and D060.
