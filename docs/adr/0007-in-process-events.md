# ADR-0007: In-process domain events, behind an interface

**Status:** Accepted · **Date:** 2026-09-09

## Context
Recording a salary change must also invalidate caches and append an audit entry. Doing that inline in
the use case couples unrelated concerns.

## Decision
Domain events (`SalaryChangeRecorded`, `BandRevised`, `EmployeeTerminated`) published via Spring's
`ApplicationEventPublisher`, consumed by `@TransactionalEventListener(AFTER_COMMIT)` handlers. The
publisher is injected as our own `DomainEventPublisher` port, not as the Spring type.

## Consequences
**Good.** Use cases stay focused. Handlers are independently testable. Because the port is ours, moving
to an outbox + broker later changes one adapter, not every use case.

**Bad.** In-process events are lost if the JVM dies after commit but before the listener runs — acceptable
for cache eviction, and the audit entry is written in the same transaction precisely because it is not.

## Rejected
- *Kafka/RabbitMQ now* — a broker to run, monitor and secure for two in-memory listeners.
- *Direct method calls* — simplest, but re-couples the modules the architecture works to keep apart.
