---
description: Orient at the start of a session — read the project context and report exactly where we are
---

You are starting a fresh session on this project. Load context before doing anything else.

Read, in this order:

1. `CLAUDE.md` — the working agreement, architecture rules, non-negotiables
2. `docs/STATUS.md` — where the previous session stopped
3. `docs/README.md` — the documentation map
4. `PLAN.md` — the day plan, and which tasks are already committed
5. `git log --oneline -25` — what has actually landed
6. `git status` — anything uncommitted or half-finished

Then read whichever of these the current task touches, and **say which ones you read**:
`docs/01-REQUIREMENTS.md`, `docs/02-DOMAIN-MODEL.md`, `docs/03-ARCHITECTURE.md`,
`docs/05-DATA-MODEL.md`, and any ADR relevant to the area you are about to change.

Report back in this exact shape, and nothing else:

```
POSITION   Day N, task X.Y — <task name from PLAN.md>
LANDED     <last 3 commits, one line each>
DIRTY      <uncommitted files, or "clean">
BLOCKERS   <from STATUS.md, or "none">
DECIDED    <any decision in STATUS.md not yet written up as an ADR, or "none">
NEXT       <the single next action, quoted from PLAN.md>
```

Then stop and wait. Do not start work, do not write code, do not suggest improvements.
Priming is orientation, not initiative.

If `docs/STATUS.md` is missing or contradicts `git log`, trust `git log` and say so — the
status file is written by hand and can go stale; commits cannot.
