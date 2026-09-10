# ADR-0009: Defer approval workflow; record the approver now

**Status:** Accepted · **Date:** 2026-09-09

## Context
Real compensation changes need multi-step approval. It is also a workflow-engine problem orthogonal
to the compensation domain that would consume most of a short build. Confirmed with the customer as
explicitly out of scope.

## Decision
Not built in v1 — confirmed out of scope by the customer. But `salary_revision` carries
`changed_by` and a mandatory `change_reason`, and the API accepts a change as a single committed
action. A future `CompensationChangeRequest` aggregate with `DRAFT → PENDING → APPROVED → APPLIED`
states can be added as a new module whose terminal transition calls the existing
`ChangeSalaryUseCase`.

## Consequences
**Good.** No wasted work; no migration needed later because the target of the workflow already exists
as a clean use case.

**Bad.** v1 is not usable in an org that mandates dual control. Stated plainly in the requirements
document rather than hidden.
