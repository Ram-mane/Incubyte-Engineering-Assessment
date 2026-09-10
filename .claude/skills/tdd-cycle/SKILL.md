---
name: tdd-cycle
description: Red-green-refactor discipline for this codebase. Use when implementing any behaviour, fixing a bug, or when asked to "make this work". Enforces test-first, minimum change, and behaviour-named tests.
---

# TDD cycle

## Red
Write exactly one failing test for one behaviour. Name it after the rule in the language of the
domain: `changing_salary_records_the_previous_amount_on_the_revision`. Not `testChange`, not
`shouldWork`.

Run it. **Confirm it fails, and fails for the right reason.** A test that passes before the
implementation exists is testing nothing. A test that errors on a missing class has not yet
demonstrated anything about behaviour.

## Green
Smallest change that makes it pass. Do not add error handling, logging, validation or a second
code path that no test demands. If you notice something else that needs doing, write it down;
do not do it now.

Touch only the files the task names. If the change requires editing a file outside that set, stop
and say why.

## Refactor
With the bar green, improve names and structure. Extract when a method stops fitting on a screen.
Re-run after every step.

## Never
- Modify a test to make an implementation pass. If the test is wrong, say so and stop.
- Write the implementation first and back-fill a test that asserts what the code already does.
- Assert on mock interactions when you can assert on an outcome. `verify(repo).save(any())` proves
  nothing; asserting the employee's new salary and the returned `SalaryRevision` proves the rule.
- Leave a `@Disabled` or commented-out test behind.

## For this domain specifically
- Inject `Clock.fixed(...)` — never let a test depend on today's date.
- Use the Object Mother builders: `anEmployee().inIndia().asSeniorEngineer().build()`.
- Salary-change tests must cover: increase, decrease, same-amount (rejected), wrong currency for
  the country, missing reason, terminated employee, and first-ever change.
- Money tests must cover cross-currency rejection, rounding at the scale boundary, and JPY's zero
  decimal places.
- For FX normalisation and audit-chain consistency, prefer one jqwik property over ten examples.

## Done
`mvn verify` green, and you can state in one sentence what behaviour now exists that did not before.
