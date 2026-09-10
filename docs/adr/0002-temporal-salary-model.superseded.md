# ADR-0002a: Salary as an append-only effective-dated history

**Status:** ❌ **Superseded by [ADR-0002](0002-current-salary-with-audit-log.md)** on 2026-09-10
· **Originally decided:** 2026-09-09

> Kept in the repository deliberately. The decision was made, then reversed on customer input, and
> the record of that is more useful than a clean history.

## Context (as understood on 9 Sep)
The brief asks for software that lets the HR Manager "answer questions about how the org pays
people". Most such questions read as historical or comparative — what did we pay last March, how has
this person progressed, who has not had a raise. A mutable `employee.salary` column answers none of
them and is what the spreadsheets already do badly.

## Decision
`salary_record` append-only with `[effective_from, effective_to)` half-open intervals,
`effective_to IS NULL` meaning current. A change inserts a row and truncates the previous open row
in one transaction. Corrections are new rows with reason `CORRECTION`. A PostgreSQL
`EXCLUDE USING gist` constraint makes overlaps physically impossible.

## Why it was superseded
Asked directly (question 3 of the clarification email, 9 Sep), Incubyte confirmed on 10 Sep:

> *"Managing the current salary per employee is completely sufficient for this assessment. Detailed
> revision logs and effective-dated salary history can be safely deferred as future enhancements."*

They also deferred the features that justified the model — point-in-time snapshots, trend analysis,
what-if simulation and review tracking.

The model was not wrong; its justification was. Every capability that paid for its complexity was
declined, and what remained — knowing what a change was and who made it — is served by a single
append-only audit table at a fraction of the cost. Keeping the temporal model after being told it
was unnecessary would have been building for my own interest rather than the customer's need.

**The lesson worth stating:** the design was sound given an assumption, and the assumption was
checkable. Asking before building is what made the reversal cost one document instead of two days.
