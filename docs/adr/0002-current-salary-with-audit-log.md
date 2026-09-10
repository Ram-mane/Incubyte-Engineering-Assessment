# ADR-0002: Current salary on the employee, with an append-only audit log

**Status:** Accepted · **Date:** 2026-09-10 · **Supersedes:** [ADR-0002a](0002-temporal-salary-model.superseded.md)

## Context
The original design modelled salary as effective-dated history (ADR-0002a). Asked directly, the
customer confirmed that current salary per employee is sufficient and that effective-dated history,
point-in-time snapshots, trend analysis and review tracking are all deferrable.

That removes every capability that justified the temporal model. But it does not remove the fact
that salary is financially significant data: a system in which a pay figure can change with no
record of its previous value or who changed it is not one to hand an HR team, however small the
build.

## Decision
- `Employee` holds `currentSalary` as an embedded `Money` (amount + currency), in the employee's
  local currency.
- Salary changes go through `Employee.changeSalaryTo(Money, ChangeReason, UserId)`, which mutates
  the current value **and returns a `SalaryRevision`**. There is no setter, so no code path changes
  pay without producing an audit record.
- `salary_revision` is append-only: previous amount, new amount, currency, reason, actor, timestamp,
  note. The application database role is granted `INSERT` and `SELECT` only — no `UPDATE`, no
  `DELETE`.
- No validity intervals, no as-of queries, no retroactive corrections, no future-dating.

## Consequences
**Good.** Roughly a fifth of the model, and the API and dashboard queries get correspondingly
simpler — the KPI cards read one column instead of resolving an interval per employee. An audit
trail exists on the data that most warrants one. The employee page gets a change log, which is a
genuine feature rather than a consolation. Delivery is roughly a day faster, on the axis the
customer said they reward.

**Bad.** The system cannot answer any historical question: no org snapshot for a past date, no
compensation trend, no "who has not had a raise in 18 months". Retroactive corrections and
future-dated changes are impossible — a raise effective next month must be entered next month. The
audit log records *that* pay changed but cannot be used to reconstruct payroll as of a date, because
nothing guarantees the log is complete back to the beginning for data loaded by bulk import.

**Trigger to revisit.** Any requirement for historical reporting, trend analysis, or compliance
evidence of what was paid on a given date. At that point ADR-0002a is the design; migrating means
seeding `salary_record` from the current value plus the existing revision log, which recovers change
dates but not intervals predating the system.

## Rejected
- *Full effective-dated temporal model (ADR-0002a)* — the right design for a different scope.
  Declined by the customer; keeping it anyway would be building for my own interest.
- *Mutable salary column with no audit at all* — the literal minimum, and defensible against the
  letter of the guidance. Rejected because an unlogged mutation of pay data is a correctness and
  trust problem that costs one table to avoid.
- *Hibernate Envers* — automatic entity auditing, near-zero code. Rejected because it audits every
  field of every entity into a generic schema, producing a log that is awkward to query, and the
  reason for a change — the field an HR manager actually cares about — has nowhere natural to live.
  An explicit domain concept beats a generic mechanism here.
