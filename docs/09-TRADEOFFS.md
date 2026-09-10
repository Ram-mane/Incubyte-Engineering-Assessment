# Trade-offs

Every entry is a place where a reasonable engineer could have chosen differently. The purpose of this
document is that the choice was made rather than defaulted into.

Scope was confirmed with Incubyte on 10 Sep 2026 ([CLARIFICATIONS.md](CLARIFICATIONS.md)). Several
rows below record decisions that *changed* as a result — the original design and why it was dropped.

| # | Choice | Gained | Paid | Would flip if |
|---|---|---|---|---|
| 1 | **Current salary + append-only audit log**, not effective-dated history | matches confirmed scope; a fraction of the model; still answers "what changed, when, by whom" | cannot answer "what did the org look like last March"; no retroactive corrections or future-dating | the customer needed historical reporting or compliance-grade point-in-time data |
| 2 | Keeping the audit log at all, when history was declared deferrable | one table and one insert buys an audit trail on financially significant data, plus a demo-able change log | slightly more than the literal minimum | the log went unused and unshown |
| 3 | Modular monolith | simplicity, local ACID, fast iteration | cannot scale one module alone | a second team, or one module with a wildly different load profile |
| 4 | PostgreSQL over SQLite | `numeric`, `percentile_cont`, trigram search, partial and covering indexes | Docker required, slower tests | the deliverable had to be a zero-dependency binary |
| 5 | Keyset pagination | O(1) paging, stable results | no page jumping, composite index per sort | users demanded "go to page N" |
| 6 | Native SQL for the dashboard | aggregation in the DB, no entity hydration, one query for four KPI cards | two persistence idioms, no compile-time SQL check | the aggregations became trivial |
| 7 | Trigram index for name search | `ILIKE '%x%'` stays indexed | an extension and a GIN index to maintain | search moved to exact-prefix only |
| 8 | Caffeine, no Redis | one fewer container to run and secure | cache duplicated per instance | more than one instance in production |
| 9 | Angular signals, no NgRx | far less boilerplate | no single state tree or time-travel debugging | three or more features shared mutable state |
| 10 | JWT, no refresh-token rotation | simple, stateless, demoable | short sessions; no server-side revocation | real production use |
| 11 | Annual base pay only | a coherent, well-modelled core | not "total rewards" | bonus/equity entered scope |
| 12 | Bands displayed, not enforced | the HR Manager sees where someone sits without the system blocking them | no alerting on out-of-band pay | the org wanted governance, not just visibility |
| 13 | No approval workflow | scope stayed on the compensation domain | not usable under dual-control mandates | the customer is enterprise-regulated |
| 14 | Synchronous bulk import | no queue, no worker, no job state machine | a very large file holds a request thread | imports exceed ~100k rows |
| 15 | Seeded FX rates | deterministic tests and demo | not live | rates materially affected decisions |
| 16 | Testcontainers over H2 | tests exercise the real engine | ~30 s slower suite | the suite exceeded the 90 s budget |
| 17 | Mutation testing in CI | tests proven to detect defects | ~2 min added to CI | build time became a bottleneck |
| 18 | Single tenant, no `tenant_id` | no speculative half-isolation | migration needed later | a second org appeared |
| 19 | Angular over React | matches the role; confirmed acceptable | slower for me than React | the team were React-first |

## The three I most expect to be challenged

**"Why keep an audit log when we told you history was deferrable?"**
Because they are different things, and only one of them was expensive. What was declined was a
*temporal query model* — validity intervals, retroactive corrections, as-of reporting — which would
have been the largest component of the system. What was kept is a single append-only table written
on each change. Salary is financially significant data; a system where a number can change with no
record of who changed it or what it was before is not one I would hand to an HR team. It cost about
two hours and it is enforced structurally: `Employee.changeSalaryTo()` returns the revision, so
there is no code path that changes pay without producing one. The database grants back that up —
the application role has no `UPDATE` or `DELETE` on the table.

**"You originally designed a temporal model. Was that wrong?"**
It was right for the problem as stated in the brief and wrong for the problem as scoped by the
customer. The brief's phrase was *"answer questions about how the org pays people"*, and most
interesting questions about pay are historical — so the first design made history the centre. When
Incubyte confirmed current-state was sufficient and deferred snapshots, trends and what-if analysis,
the justification for that model disappeared with them. Rather than quietly keeping it,
[ADR-0002](adr/0002-current-salary-with-audit-log.md) supersedes the original decision and records
what changed and why. Reversing a design you liked because the customer told you it was not needed
is the job.

**"You said scalable, but you built a monolith with an in-process cache."**
Deliberate. 10,000 employees is ~25 MB; even at 1M it is ~2.5 GB with ~3M audit rows — small for
PostgreSQL. The workload is read-heavy and write-light, so the scaling axis is concurrent reads,
solved by stateless instances plus read replicas, not by decomposition.
[06-SCALABILITY.md](06-SCALABILITY.md) gives the ordered path with a measurable trigger for each step.

## What I would do next, given another week

1. Effective-dated history, properly — now genuinely justified only if the customer wants trends,
   snapshots or compliance reporting.
2. Band enforcement and alerting on out-of-band pay.
3. Approval workflow (ADR-0009).
4. Total-rewards components as typed siblings of base pay.
5. Async import with a transactional outbox.
6. Refresh-token rotation and server-side revocation.
