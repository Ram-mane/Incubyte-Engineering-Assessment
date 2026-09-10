---
description: Close out a session — update STATUS.md so the next session can pick up cold
---

End-of-session handoff. The next session will start from `docs/STATUS.md` and nothing else,
so write it for someone with no memory of today.

1. Run `git log --oneline` for today's commits and `git status`.
2. Rewrite `docs/STATUS.md` completely (do not append — a status file that grows is a status
   file nobody reads), keeping the existing headings:

   - **Position** — day and task number from `PLAN.md`, and whether it is complete
   - **Landed today** — commits, one line each
   - **Uncommitted** — anything dirty, and *why* it was left that way
   - **Blockers** — anything broken, flaky, or waiting on an external thing. Be specific:
     "Render deploy 502s on cold start, ~40s" not "deploy issue"
   - **Decisions recorded** — the IDs added to `docs/DECISIONS.md` this session
   - **Next action** — the single next thing, quoted from `PLAN.md` with its task number
   - **Gotchas** — anything that cost time today and would cost it again

3. **Sweep for unrecorded decisions.** Re-read this session's exchanges and list every choice
   that was made and not yet in `docs/DECISIONS.md` — including ones I made in conversation
   without saying "decide this", ones you proposed and I approved, and anything you did one way
   after considering another. For each, show me the proposed row and wait; do not write them
   unprompted.

   Include the small ones. A decision that felt obvious at the time is exactly the one nobody
   can reconstruct three days later, and "why is it done this way" is the question the interview
   will actually ask.

   Then check every row with status `Probation` — if its condition is now settled, say so and
   propose the resolution.

4. Tell me if any decision in that list deserves an ADR, and offer to write it with `/adr`.
5. Commit as `docs: update session status`.

Be honest about what is broken. A status file that says everything is fine when the deploy is
down wastes the first twenty minutes of the next session.
