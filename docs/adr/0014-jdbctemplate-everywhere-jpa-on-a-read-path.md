# ADR-0014: Every adapter is JdbcTemplate; JPA is demonstrated on a read path

**Status:** Accepted · **Date:** 2026-09-12 · **Supersedes the write half of**
[ADR-0005](0005-native-sql-for-analytics.md)

## Context

ADR-0005 split persistence: native SQL for the `analytics` module, JPA and Spring Data for writes.
Day 2 built the whole product — directory, filters, search, employee detail, revision log and the
pay-change use case — and put every adapter on `JdbcTemplate`. There are no `@Entity` classes.

That is not drift to be tidied up, because between ADR-0005 and now the write path acquired an
invariant an ORM cannot be trusted with, and the divergence has to be settled one way or the other.

The invariant is the reason this system exists: **pay cannot change without an audit record.**
`Employee.changeSalaryTo` mutates the current salary *and* returns the `SalaryRevision` that records
it, there is no setter, and both writes happen in one explicit transaction (D019, and the
forced-failure proof in `ChangeSalaryIsAllOrNothingIT`).

The brief is the other force. `05-DATA-MODEL.md` records that Incubyte asked to see "server-side
pagination, indexing, efficient filtered search and **JPA query performance**" demonstrated against
10,000 records. Three of those four are demonstrated with committed `EXPLAIN` plans. The fourth is
not, and a submission that simply omits it is not making an argument.

## Decision

**Every persistence adapter uses `JdbcTemplate`.** Reads return flat `record` projections of exactly
the columns a screen shows. Writes are explicit statements: a pay change is `UPDATE employee` plus
`INSERT INTO salary_revision`, in one `@Transactional` use case, in that order, written where the
code says they are written.

**No `@Entity` class exists, and the `Employee` aggregate will not become one.** The reason is not
taste and not performance — it is that **dirty checking is an ambient write path**. A managed
`Employee` whose salary field is mutated flushes an `UPDATE` at commit with no `SalaryRevision`
anywhere, and nothing in this codebase would catch it:

- the ArchUnit rule (D113) forbids *constructing* a `SalaryRevision` outside the domain; it cannot
  forbid *omitting* one;
- the `INSERT`/`SELECT`-only grant (D020, D097) restricts `salary_revision`, while the application
  role holds `UPDATE` on `employee` because legitimate pay changes need it;
- and `changeSalaryTo` returning the revision only guarantees a revision *exists* — it cannot
  guarantee that the salary reached the database through it.

Every guard in this system is aimed at making an unrecorded pay change impossible to express. An ORM
that writes fields because they changed makes it the default. The absent setter, the returned
revision and the restricted role were all built to stop ambient writes; reintroducing one under the
persistence layer would undo them from below.

**The JPA criterion is met on a read path, after 3.2 and the dashboard ship.** One read — employee
with their salary band, or revisions with their actor — is mapped with JPA, written first in the
shape that produces an N+1, caught by the existing statement-count harness (`CountingDataSource`),
then fixed with an entity graph, with the before and after statement counts committed as evidence.
That demonstrates JPA query performance, which is what was asked, against the write path staying
exactly as it is. `spring-boot-starter-data-jpa` stays a dependency for that slice.

`03-ARCHITECTURE.md` §7 currently says "writes go through JPA entities"; it is wrong, and is
corrected in the same commit as this ADR.

## Consequences

**Good.** There is no code path that changes pay without producing the record of it, and no
framework that can create one by inference. The two statements of a pay change are the two
statements in the source, which is what makes the forced-failure test meaningful: it drops the
revision `INSERT` after the employee `UPDATE` has been issued and asserts the salary did not move,
and a persistence context would have turned that ordering into a Hibernate-scheduled flush. Reads
are SQL somebody wrote, `EXPLAIN`-able from source, and the plans in `docs/evidence/` are plans of
the query the application actually sends.

**Bad.**

- **There is no optimistic locking, and `04-API-DESIGN.md` promises `ETag`/`If-Match` on employee
  updates.** Two managers changing the same salary concurrently both succeed: the log gets two
  revisions whose `previous_amount` is the same figure, and the employee ends on whichever `UPDATE`
  committed second. The audit log then describes a history that never happened. This is the most
  serious cost here and it is not hypothetical — it needs a `version` column and a conditional
  `UPDATE … WHERE version = ?`, which is a schema change plus a use-case change, and neither is
  scheduled.
- **Row mapping is hand-written and unchecked against the schema.** A renamed column compiles and
  fails at runtime; only the integration tests stand between that and a deploy.
- **`spring.jpa.hibernate.ddl-auto: validate` currently validates nothing**, because there are no
  entities. It is a configured gate with no subject — the same species this project has now found
  three times (D118), sitting in our own `application.yml`. It stays only because the read-path
  slice will give it something to check; until then it should be read as scaffolding, not as a
  guarantee that the schema and the code agree.
- **Hibernate still initialises on every boot** to manage zero entities, on a free-tier instance
  whose cold start is already the worst thing about the demo.
- **The brief's fourth criterion stays unanswered until that slice lands.** If the build runs out of
  time before the dashboard is finished, it is never answered, and the mitigation is a plan rather
  than a thing that exists.

**Trigger to revisit.** Not "when the codebase grows". Two specific events:

- The first transaction that must update **two aggregates together**. Hand-written statements stop
  being simpler than an identity map at that point, and the `UPDATE`-per-aggregate ordering becomes
  something worth delegating.
- The first `Employee` that acquires a **real object graph** — a manager chain walked in code, an
  employment history, bands loaded per employee — rather than the flat row it is today.

Concurrency does **not** trigger it: optimistic locking arrives as `WHERE version = ?` in SQL, which
needs no ORM.

## Rejected

- **JPA for the write path, as ADR-0005 and 03-ARCHITECTURE describe.** This was proposed and
  rejected on the ambient-write argument above: a mutated managed entity flushes an `UPDATE` that no
  guard in this system sees, which is precisely the failure the whole design exists to prevent.
  Two further breaks were concrete rather than theoretical. `ddl-auto: validate` would fail at
  **startup** on `country_code char(2)` and `salary_currency char(3)`, which Hibernate maps as
  `varchar` — a boot failure on the live deployment, and this project has already lost one deploy
  to a startup failure. And `ChangeSalaryIsAllOrNothingIT` asserts two explicit statements inside
  one transaction; through a persistence context those become a Hibernate-ordered flush, so this
  morning's forced-failure proof would stop describing what the code does while continuing to pass.
- **Spring Data JDBC for the write path.** Aggregate mapping with no persistence context and no
  dirty checking, so the ambient-write objection mostly falls away — `save(employee)` must be called
  explicitly. It loses because it narrows the hole rather than closing it: a `save` after a mutation
  that did not go through `changeSalaryTo` still writes a salary with no revision, now requiring one
  line instead of zero. For that it adds a third persistence idiom beside the projections and the
  explicit inserts, and it is not JPA, so it answers the brief's fourth criterion no better than
  what is already here.
- **Map `Employee` with JPA but never mutate a managed instance** — load detached, copy, write
  explicitly. It preserves the invariant on paper and fails on the first developer who does the
  normal thing, because mutating a loaded entity is the framework's default behaviour and the safe
  path would be the unusual one. A rule that depends on everyone knowing not to use a framework the
  way it is designed is a rule that is already broken.
