---
name: commit-discipline
description: Commit message and granularity rules. Use before every commit. The git history is explicitly graded on this project.
---

# Commits

The history is part of the deliverable. It should read as the story of how the solution evolved.

## Format
`<type>(<scope>): <imperative summary, lowercase, no trailing period>`

Types: `feat` `fix` `test` `refactor` `perf` `docs` `chore` `ci` `style`

Body (when the *why* is not obvious): what forced this, what was considered, what it costs.
Reference the ADR: `Refs ADR-0002`.

## Granularity
One logical change per commit. Each commit builds and passes tests on its own.

**Good sequence:**
```
feat: add half-open date range value object
test: prove salary history invariants with property tests
feat: add salary history with interval truncation
feat: add temporal salary schema with overlap constraint
test: verify database rejects overlapping salary intervals
perf: add partial index for open salary intervals
```
Someone can read that and see the design emerging.

**Bad:**
```
wip
fixes
added backend
final commit
```

## Rules
- Never one giant "implement everything" commit. It hides the process, which is what is being assessed.
- Never a commit that leaves the build red.
- Docs land with the code they describe, or before it.
- Do not squash the history before submitting. The evolution is the point.
- No AI attribution noise in messages unless the project asks for it — `docs/08-AI-WORKFLOW.md` is
  where the AI story is told properly.

## Before committing
1. `git diff --staged` and read it. All of it.
2. `mvn verify` green.
3. Nothing unrelated staged — no stray formatting, no debug logging, no commented-out code.
4. No secrets, no `.env`, no local config.
