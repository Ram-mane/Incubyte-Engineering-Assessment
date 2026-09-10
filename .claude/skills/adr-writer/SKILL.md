---
name: adr-writer
description: Write an Architecture Decision Record for this project. Use whenever a decision is made that would be expensive to reverse, or that a reviewer would reasonably question.
---

# Writing an ADR

Location `docs/adr/NNNN-kebab-title.md`, next number in sequence. Commit it **when the decision is
made**, not at the end of the project — the timestamp in git is part of the evidence that the
thinking preceded the code.

## Template
```markdown
# ADR-NNNN: <decision in one line, active voice>

**Status:** Accepted · **Date:** YYYY-MM-DD

## Context
The forces. What is true that makes this a decision rather than an obvious default? Constraints,
scale, team, deadline. No solution here.

## Decision
What we are doing, stated so someone could implement it. Present tense, active voice.

## Consequences
**Good.** What this buys.
**Bad.** What it costs. **Be specific.** An ADR with no downsides is marketing.
**Trigger to revisit.** The measurable condition under which this should change.

## Rejected
Each realistic alternative, and the actual reason it lost. "Too complex" is not a reason;
"adds a broker to run and secure for two in-memory listeners" is.
```

## Standard
- One decision per ADR.
- The **Rejected** section is the one reviewers read most carefully. A candidate who lists the
  alternative they rejected and *why* is demonstrating judgment; one who lists only their choice is
  demonstrating preference.
- Never edit an accepted ADR to change the decision. Write a new one and mark the old
  `Superseded by ADR-NNNN`.
- Write it before or as you implement. An ADR reconstructed afterwards reads like one, and reviewers
  can tell.
