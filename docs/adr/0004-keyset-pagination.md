# ADR-0004: Keyset (cursor) pagination, not offset

**Status:** Accepted · **Date:** 2026-09-09

## Context
The directory has 10,000 rows today and is designed for 1M. HR managers scroll and filter it constantly.

## Decision
All collection endpoints expose an opaque `cursor` encoding the last-seen sort key. No `page` parameter.
Responses return `nextCursor` and an approximate total from statistics rather than an exact `COUNT(*)`.

## Consequences
**Good.** Constant-time paging at any depth. Stable under concurrent inserts — no skipped or duplicated
rows. No expensive `COUNT(*)` on every page.

**Bad.** No "jump to page 500". Cursors must encode a total ordering, so every sort adds a composite
index. Approximate totals need explaining in the UI ("about 10,000").

## Rejected
- *Offset pagination* — simpler and what most candidates will use, but O(offset) and unstable. Choosing
  it would contradict the scalability claims made elsewhere in this repo.
