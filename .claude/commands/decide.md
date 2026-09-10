---
description: Record a decision in docs/DECISIONS.md with its reasoning
---

Record this decision: **$ARGUMENTS**

Append a row to the correct section of `docs/DECISIONS.md`, with the next sequential ID.

Before writing, ask me anything you need about the forces behind it. **Do not invent a rationale** —
a reconstructed reason is worse than no entry, because it reads like evidence.

The row is `| ID | Decision | Why | Status |`:

- **Decision** — what we are doing, active voice, one clause. Not what we considered.
- **Why** — the reason that actually decided it, in one sentence. Where a concrete failure was
  avoided, name it: "`salary.minus(band.min())` would throw for exactly the below-min employees
  the feature exists to surface" beats "cleaner design". If the choice reversed an earlier one,
  say what changed — new information, customer input, or a mistake found.
- **Status** — `Active`, `Superseded by Dnnn`, `Reversed`, or `Probation` with the condition and
  date that will settle it.

Rules:

1. **Never edit an existing row's decision or reasoning.** Superseding a decision means adding a
   new row and setting the old one to `Superseded by Dnnn`. The log is append-only for the same
   reason `salary_revision` is.
2. **Apply the graduation rule.** If the *Why* needs more than one sentence, this is an ADR. Say
   so, offer `/adr`, and reduce the row to a pointer once the ADR exists.
3. If the decision reverses one already recorded, update the old row's status in the same edit.
4. Put it in the section it belongs to, not at the end of the file.

Then show me the row and the section it landed in. Commit with the code it relates to, or as
`docs: record decision on <topic>` if it stands alone.
