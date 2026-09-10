# ADR-0001: Modular monolith over microservices

**Status:** Accepted · **Date:** 2026-09-09

## Context
The system serves one organisation, ~10,000 employees, one team, one product. The brief asks for
software that could grow, and explicitly says it is "not looking for the most complex system".

## Decision
One Spring Boot deployable containing four business modules (`employee`, `compensation`, `band`,
`analytics`) with boundaries enforced by ArchUnit tests, each internally hexagonal.

## Consequences
**Good.** One thing to deploy, debug and observe. Transactions are local ACID, not sagas. Refactoring
across a boundary is a compiler error, not a versioned contract negotiation. Sub-millisecond
"inter-service" calls.

**Bad.** Cannot scale one module independently. A memory leak anywhere affects everything. Boundary
discipline depends on tests rather than the network — mitigated by making those tests part of the build.

**Trigger to revisit.** When one module needs materially different scaling, or a second team owns a
module, `analytics` splits out first: it is read-only and has no inbound dependencies.

## Rejected
- *Microservices now* — network boundaries, distributed transactions and 4× the ops surface to solve
  problems a 120k-row dataset does not have. This would be the single clearest signal of poor calibration.
- *Layered monolith (controller/service/repository)* — cheapest to write, but the boundaries that make
  future extraction possible would not exist.
