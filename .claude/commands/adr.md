---
description: Write an ADR for a decision just made
---

Write an ADR for: **$ARGUMENTS**

Use the `adr-writer` skill. Before writing, ask me anything you need about the forces behind the
decision — do not invent context.

Requirements:
- Next number in `docs/adr/`.
- The **Consequences → Bad** section must contain real costs, specifically stated. If you cannot name
  a genuine downside, you have not understood the decision; tell me instead of writing filler.
- The **Rejected** section must name at least two realistic alternatives with the actual reason each
  lost. "Too complex" is not a reason.
- Add a **Trigger to revisit** with a measurable condition.

Then commit it alone: `docs: record decision on <topic>`.
