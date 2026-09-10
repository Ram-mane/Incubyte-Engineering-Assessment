---
description: Day 3 afternoon — evidence, polish, deploy, submit (tasks 3.11-3.22)
---

Execute **PLAN.md tasks 3.11 through 3.22**. This is the last session before submission.

**Evidence, not claims** — everything in 3.11–3.13 exists so the README can point at a committed
artefact instead of an adjective:
- k6 with thresholds that **fail the run** if missed; commit the report and the `EXPLAIN` plans
- a 100,000-employee seed run for the comparison curve
- Pitest to ≥ 70% on domain; when it finds a surviving mutant, **fix the test, never lower the
  threshold**. Tell me each mutant it found and how you killed it — those are interview material.

**Polish** — Playwright on the five journeys, an axe pass, and empty/loading/error states on every
screen. An unhandled empty state is the most common thing a reviewer trips over in the first minute.

**Ship** — OpenAPI published, `docs/08-AI-WORKFLOW.md` finalised with the real prompts and the
genuinely rejected outputs, README with live URL, demo credentials, doc index and video link, final
deploy, smoke test.

Then stop. I record the video (3–5 min, script in `PLAN.md`) and send the link.

**Before you call it done, verify honestly and report failures plainly:**
- `mvn verify` green from a clean clone
- `docker compose up` works from scratch on a fresh machine
- the live URL is up, seeded, and both demo logins work
- every claim in the README is backed by something in the repo
- no secrets, no `.env`, no commented-out code, no `TODO` left in the diff

If any of these fails, say so rather than reporting success. A README that overstates is worse than
one that admits a gap.

Finish with `/wrap`.
