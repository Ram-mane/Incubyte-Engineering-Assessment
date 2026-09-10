---
name: project-discovery
description: Orient in a codebase before changing it — map the architecture, database, conventions and test strategy from the code itself, and write the findings to docs/ in a layout future sessions can rely on. Use when starting on an unfamiliar project, when docs/ is missing or stale, when asked "how does this work", or before a change that spans more than one module.
---

# Project discovery

Reading a codebase is cheap. Guessing at one and being confidently wrong is expensive. Run this
before the first change in an unfamiliar project, and whenever the documentation and the code
disagree.

**The rule that governs everything below: report what the code says, not what it should say.** If
the architecture is inconsistent, the finding is the inconsistency. Do not tidy it into a clean
story, and do not describe an intended design as if it were the actual one.

## 1. Orient (5 minutes, before reading any application code)

```bash
ls -a                                   # config, tooling, hidden conventions
cat README.md CLAUDE.md 2>/dev/null
cat docs/STATUS.md docs/README.md 2>/dev/null
git log --oneline -30
git log --format='%an' | sort | uniq -c | sort -rn | head   # who knows this code
```

Build file first — `pom.xml`, `build.gradle`, `package.json`. It tells you the language level, the
frameworks, the test stack, and which quality gates are enforced versus merely installed. A
dependency that is present but not wired into the build is a tool someone tried and abandoned; that
is a finding.

## 2. Architecture

Find the boundaries before the details.

```bash
find . -name '*.java' -not -path '*/target/*' | sed 's|/[^/]*$||' | sort -u | head -40
```

Ask, and answer with evidence:

- **Is there a layering, and is it enforced?** Look for ArchUnit, Spring Modulith, ESLint import
  rules, module-info. Enforced boundaries and aspirational ones behave very differently under change.
- **Where does business logic actually live?** Grep the controllers. Logic in controllers or in
  fat services means the domain objects are anaemic, whatever the package names suggest.
- **Which direction do dependencies point?** If `domain` imports the framework, it is not hexagonal
  regardless of the folder names.
- **Where are the ports declared?** A repository interface sitting next to its JPA implementation
  has inverted nothing.
- **What crosses process boundaries?** HTTP clients, queues, schedulers, caches.

## 3. Database

```bash
ls src/main/resources/db/migration/     # Flyway
ls src/main/resources/db/changelog/     # Liquibase
```

Read migrations **in order**, not just the latest schema. The sequence shows what the team learned:
a column added then dropped, an index added under load, a constraint retrofitted after a bug.

For each significant table record: purpose, natural key, every index **and the query each serves**,
constraints, whether it is append-only, and its rough row count. An index nobody can attribute to a
query is either dead weight or an undocumented access path — both worth flagging.

Check the things that only show up under data volume: `OFFSET` pagination, `ILIKE '%x%'` without a
trigram index, `float`/`double` on money, missing foreign keys, `SELECT *` into entities for
aggregation, and N+1 patterns in mapped collections.

```bash
grep -rn 'double\|float' --include='*.java' | grep -i 'amount\|price\|salary\|total\|rate'
grep -rn 'OFFSET\|setFirstResult' --include='*.java'
grep -rn 'FetchType.EAGER' --include='*.java'
```

## 4. Conventions

Infer from the code, not from a style guide nobody follows. Sample three or four files in the same
layer and look for what they agree on: naming, error handling, validation placement, DTO mapping,
null handling, logging, transaction boundaries, package-private versus public.

Where files disagree, note which pattern is in the **newest** commits — that is the one the team is
moving toward, and the one new code should follow.

## 5. Tests

```bash
find . -path '*/test/*' -name '*Test*.java' | wc -l
```

Read three tests, not thirty. Determine: what level most tests sit at; whether they assert outcomes
or mock interactions; whether they use a real database or an in-memory substitute; whether they are
deterministic (grep for `now()`, `Random`, `Thread.sleep`); and how long the suite takes.

Then the question that matters: **if a bug were introduced here, would a test fail?** High coverage
with interaction-only assertions answers no. Say so plainly if that is what you find.

## 6. Write it down

Produce `docs/00-ORIENTATION.md`. Keep it under two pages — a map, not a transcript.

```markdown
# Orientation
_Generated from the code at <commit sha>, <date>. Regenerate rather than edit._

## What this system does
Two sentences, in domain language.

## Shape
Deployables, modules, and how they talk. One mermaid diagram if it earns its place.

## Where things live
| I want to change... | Look in... |

## Database
Key tables, their keys, and the indexes that matter — with the query each one serves.

## Conventions in force
The patterns new code should follow, with a file to copy from.

## Tests
Levels, tooling, how to run them, how long they take.

## Sharp edges
Things that will bite: inconsistencies, known-slow paths, undocumented coupling, stale docs.
Be specific and unflattering. This section is the reason the document is worth reading.

## Open questions
What could not be determined from the code, and who to ask.
```

Then, if the project has no `docs/STATUS.md`, create one — it is what makes the *next* session cheap.

## 7. The standard docs layout

Once discovery has run, keep documentation in this shape so any session can navigate it without
searching:

```
docs/
├── README.md            entry point — the reading order
├── STATUS.md            living: where we are, what is broken, next action
├── 00-ORIENTATION.md    generated map of the code as it is
├── 01-REQUIREMENTS.md   what we are building, and what we deliberately are not
├── 02-DOMAIN-MODEL.md   aggregates, value objects, invariants
├── 03-ARCHITECTURE.md   modules, boundaries, diagrams
├── 04-API-DESIGN.md     the contract
├── 05-DATA-MODEL.md     schema, indexes and their reasons, query patterns
├── 06-SCALABILITY.md    built vs designed-for, with triggers
├── 07-TEST-STRATEGY.md  how correctness is proven
├── 08-AI-WORKFLOW.md    how AI was used, including where it was wrong
├── 09-TRADEOFFS.md      decisions that could have gone the other way
├── 10-SECURITY.md       model and known gaps
├── adr/NNNN-slug.md     one decision each, with rejected alternatives
└── perf/                k6 reports, EXPLAIN plans — evidence, not claims
```

Numbered so reading order is obvious. Stable filenames so `CLAUDE.md` and commands can reference
them. `STATUS.md` and `00-ORIENTATION.md` are regenerated; the numbered files are written once and
amended; ADRs are never edited after acceptance — they are superseded by a new one.

## What discovery is not

Not a refactor. Not a code review. Not a list of improvements. If you spot something genuinely
alarming — a credential in the repo, money in a `double`, an unparameterised query — say it once,
clearly, in **Sharp edges**, and carry on mapping. Fixing it is a separate decision the human makes.
