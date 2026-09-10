---
description: Day 0 — commit the planning docs before any code, in the order that tells the story
---

Execute **PLAN.md Day 0 only**. Stop at the end of it.

Five separate commits, in exactly this order. The ordering is deliberate and is part of what is
being assessed — do not combine them, do not reorder them.

1. `chore: initialise repository` — `.gitignore`, `LICENSE` (MIT, Ram Subhas Mane, 2026), `README.md`
2. `docs: add requirements, domain model and architecture` — `docs/01`–`07`, `09`, `10`, `docs/README.md`
3. `docs: record initial architecture decisions` — `docs/adr/0001`–`0011`, **excluding**
   `0002-current-salary-with-audit-log.md` and `0012-scope-confirmed-with-customer.md`
4. `chore: add AI development guardrails` — `CLAUDE.md`, `.claude/`
5. `docs: revise scope after customer clarification` — `docs/CLARIFICATIONS.md`,
   `docs/adr/0002-current-salary-with-audit-log.md`, `docs/adr/0012-...md`, `PLAN.md`, `docs/STATUS.md`

Before each commit: show `git status` and the exact file list you intend to stage, then wait for
my go-ahead. Never `git add -A`.

**Do not fabricate history.** The docs in commits 2 and 3 were already revised for the confirmed
scope; that is fine. Commit 5 carries the narrative of the reversal through the two ADRs and the
clarifications table. Do not attempt to reconstruct "pre-clarification" versions of the other docs.

Finish with `/wrap`.
