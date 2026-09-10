# ADR-0011: How AI tooling is used on this project

**Status:** Accepted · **Date:** 2026-09-09

## Context
The assessment requires AI-assisted development and asks to see how the tools were used.

## Decision
Claude Code is the primary implementation tool, constrained by a committed `CLAUDE.md`, project
skills and slash commands (see `.claude/`). The division of labour is explicit:

**I decide, AI does not:** the domain model, aggregate boundaries, the temporal design, every ADR,
API shape, test strategy, and what to leave out.

**AI drafts, I review line by line:** implementations from a specified test, JPA mappings, DTO/mapper
boilerplate, Angular components from a described interaction, Flyway migrations, the seed generator,
k6 scripts.

**AI is not trusted with:** money arithmetic, FX normalisation, security config, or any SQL behind
the dashboard — these are hand-written or hand-verified, because they are where a plausible-looking
wrong answer is most expensive and least visible.

Every session's operative prompts are logged in `docs/08-AI-WORKFLOW.md`.

## Consequences
**Good.** Speed on mechanical work; the interesting decisions stay human and defensible in interview.
Committed prompts make the process reproducible.

**Bad.** Reviewing generated code is real work and is budgeted for. The guardrail against
plausible-but-wrong output is the test-first discipline: a failing test exists before the AI writes
the implementation, so "looks right" is never the acceptance criterion.
