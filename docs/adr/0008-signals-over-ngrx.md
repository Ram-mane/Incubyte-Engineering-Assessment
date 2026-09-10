# ADR-0008: Angular signals + feature facades, not NgRx

**Status:** Accepted · **Date:** 2026-09-09

## Context
The SPA has modest client state: filters, a selected employee, dashboard parameters. Almost everything
displayed is server state that should be fetched, not mirrored.

## Decision
Angular 19 standalone components with signals for local state and a facade service per feature owning
server calls and exposing signals. No NgRx, no global store.

## Consequences
**Good.** Far less boilerplate — no actions, reducers, effects or selectors for what is ultimately a
fetch. Change detection is fine-grained. Components stay testable because they depend on a facade
interface, not `HttpClient`.

**Bad.** No time-travel debugging or a single inspectable state tree. Cross-feature state, if it appears,
needs a convention rather than a framework.

**Trigger to revisit.** If three or more features need to share mutable client state, introduce a store
for those features only.

## Rejected
- *NgRx* — excellent for large apps with complex shared client state. Using it here would be ceremony
  that reviewers read as inability to right-size a solution.
