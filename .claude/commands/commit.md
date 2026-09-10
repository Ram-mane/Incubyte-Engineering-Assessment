---
description: Review the diff and make one well-scoped conventional commit
---

Follow the `commit-discipline` skill.

1. `git status` and `git diff` — show me what has changed.
2. If the changes span more than one logical concern, **say so and propose how to split them into
   separate commits.** Do not bundle.
3. Confirm `mvn verify` is green. If you have not run it, run it.
4. Check for: secrets, `.env`, debug logging, commented-out code, unrelated formatting churn.
5. Propose the message — conventional format, imperative, lowercase summary. Include a body only if
   the *why* is not obvious from the summary, and reference an ADR if one applies.
6. Show me the message and wait for my go-ahead before committing.

Never `git add -A` without listing what it would stage. Never squash existing history.
