# Documentation index

Read in this order:

1. **[01-REQUIREMENTS.md](01-REQUIREMENTS.md)** — the one-pager. Goal, scope, exclusions.
2. **[02-DOMAIN-MODEL.md](02-DOMAIN-MODEL.md)** — why pay cannot change without an audit record, and the ten invariants.
3. **[03-ARCHITECTURE.md](03-ARCHITECTURE.md)** — modular monolith, hexagonal modules, C4 diagrams.
4. **[05-DATA-MODEL.md](05-DATA-MODEL.md)** — the schema, the append-only log, and every index's reason to exist.
5. **[04-API-DESIGN.md](04-API-DESIGN.md)** — the contract.
6. **[07-TEST-STRATEGY.md](07-TEST-STRATEGY.md)** — how correctness is proven.
7. **[06-SCALABILITY.md](06-SCALABILITY.md)** — built vs designed-for, with triggers.
8. **[09-TRADEOFFS.md](09-TRADEOFFS.md)** — every decision that could have gone the other way.
9. **[10-SECURITY.md](10-SECURITY.md)** — model and known gaps.
10. **[08-AI-WORKFLOW.md](08-AI-WORKFLOW.md)** — how AI was used, and where it was wrong.
11. **[adr/](adr/)** — the decision records.
12. **[CLARIFICATIONS.md](CLARIFICATIONS.md)** — the ten questions asked before building, and what the answers changed.

Note `adr/0002-temporal-salary-model.superseded.md`: a decision made, then reversed on customer
input. It is kept deliberately — the reversal is more informative than a tidy history.

`perf/` holds k6 reports and `EXPLAIN ANALYZE` plans, committed as evidence rather than claims.
