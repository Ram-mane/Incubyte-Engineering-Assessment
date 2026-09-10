# ADR-0012: Build to the customer's confirmed scope, not the inferred one

**Status:** Accepted · **Date:** 2026-09-10

## Context
The assessment brief is short and open to reading. From it I inferred a compensation-intelligence
product built on a temporal data model, with seven analytical questions, band enforcement and
what-if simulation. That inference drove the first version of every document in `docs/`.

Rather than build on the inference, I sent ten clarifying questions before writing code. The
answers ([CLARIFICATIONS.md](../CLARIFICATIONS.md)) narrowed the scope materially:

- current salary sufficient; effective-dated history deferred
- dashboards with KPI cards and interactive filters are the target, not a query engine
- what-if simulation, point-in-time snapshots and review tracking deferred
- bands displayed, not enforced
- annual base salary only, in local currency, with a seeded FX table normalising to USD
- 10,000 records to be used to demonstrate pagination, indexing, search and query performance

## Decision
Rebuild the requirements, domain model, data model, API and plan to the confirmed scope before
writing any code. Supersede the affected ADRs rather than editing them. Keep the superseded versions
in the repository.

## Consequences
**Good.** The delivered system matches what the customer asked for. Roughly a day of build time
returned, on the axis they said they prioritise. The scope changes are visible as a documented
reversal rather than an unexplained gap, and the `docs/` history shows a design responding to
information.

**Bad.** Some of the first day's design work is now superseded. The submission is a smaller system
than originally planned, so the engineering has to show in how well the smaller thing is built —
correctness of the money model, indexing and query evidence, test quality — rather than in
architectural ambition.

**Trigger to revisit.** Not applicable. This ADR records a process decision that has already played
out; it exists so a reviewer can see why the model changed between commits.

## Rejected
- *Build to the inferred scope anyway.* The extra capability might have impressed, but the role is
  literally titled Software Craftsperson, and overriding explicit written scope guidance is the
  clearest way to demonstrate the opposite of craftsmanship. Listening is part of the discipline.
- *Silently adopt the new scope and quietly rewrite the docs.* Cheaper, and it would have hidden the
  most interesting thing that happened during the build: a design being reversed on evidence.
