# AI Workflow

The brief asks for the prompts and instructions used with AI tools. This is that record, and it is
written as the work happens rather than reconstructed afterwards.

## Setup

Claude Code, constrained by committed configuration rather than ad-hoc chat:

| File | Purpose |
|---|---|
| `CLAUDE.md` | Standing context: architecture rules, coding standards, TDD loop, definition of done. Loaded into every session automatically. |
| `.claude/skills/tdd-cycle/` | Enforces red → green → refactor, one behaviour at a time |
| `.claude/skills/hexagonal-module/` | How to add a use case without violating the layering |
| `.claude/skills/angular-feature/` | Standalone component + facade conventions |
| `.claude/skills/adr-writer/` | ADR format and the standard for "rejected alternatives" |
| `.claude/skills/commit-discipline/` | Conventional commits; one logical change each |
| `.claude/commands/new-usecase.md` | Scaffolds port → test → service → controller in the right order |
| `.claude/commands/review.md` | Runs the craftsmanship review checklist over a diff |
| `.claude/agents/code-reviewer.md` | Adversarial reviewer subagent, run before each commit |
| `.claude/agents/test-auditor.md` | Checks tests assert behaviour, not implementation |

Putting the standards in version control rather than in a chat window is the point: the constraints
are reviewable, they apply to every session, and a reader of this repo can see exactly what the AI was
told.

## Division of labour

**Human-only.** Domain model and aggregate boundaries. The decision to reverse the temporal design
after customer input. Every ADR. API shape. What to leave out. Test strategy. Money arithmetic and
FX normalisation. Security config. Dashboard SQL (hand-written, then `EXPLAIN`-verified).

**AI-drafted, human-reviewed line by line.** Implementations written to satisfy a test I wrote first.
JPA mappings and Flyway migrations. DTOs, mappers, controllers. Angular components from a described
interaction. The seed generator. k6 scripts. Documentation prose from my bullet points.

**Rejected AI output is also recorded** in §4 below — the failures are more informative than the successes.

## The loop actually used

1. I write the failing test, by hand, naming the behaviour.
2. `/tdd` — Claude makes it pass with the minimum change, touching only the named files.
3. `/review` — the reviewer subagent checks the diff against the checklist.
4. I read the diff myself. Every line.
5. `/commit` — one conventional commit for one logical change.

Step 4 is not optional and is not delegated. The test-first order is the guardrail: AI output is
accepted because a test I wrote passes, never because the code looks plausible.

## Representative prompts

> **Domain.** "Here is `Employee` with a failing test `changing_salary_records_the_previous_amount_on_the_revision`. Implement `changeSalaryTo(Money, ChangeReason, UserId)` so it updates `currentSalary` and returns the `SalaryRevision`. Pure Java only — no Spring, no JPA, no `Instant.now()`; the `Clock` is injected. There must be no setter for `currentSalary`. Do not modify the test."

> **Persistence.** "Write the Flyway migration for `salary_revision` per docs/05-DATA-MODEL.md, granting the application role INSERT and SELECT only. Then write an integration test that attempts an UPDATE and a DELETE as that role and asserts the database rejects both."

> **Dashboard.** "I have written this native query for the KPI summary — spend, headcount, average and median in one pass with FX normalisation. Review it for correctness against the schema, then write an integration test asserting both the result and that `EXPLAIN` shows an index scan on `ix_employee_rollup`. Do not change the SQL without telling me why."

> **Angular.** "Create a standalone `RevisionLogComponent` using signals, taking a `SalaryRevision[]` input, rendering a list with reason chips, actor, timestamp, and both amounts formatted through the shared money pipe. Tests must query by role and accessible name, never by CSS class."

> **Seeding.** "Write `SeedDataGenerator` producing 10,000 employees across six countries and ~30,000 salary revisions with a fixed RNG seed. Salaries must be in each employee's country currency. Use JDBC batch inserts, not JPA. Must complete in under 20 seconds and be idempotent."

## Where AI got it wrong (and what that taught me)

| # | What it produced | Why it was wrong | Fix |
|---|---|---|---|
| 1 | A `setCurrentSalary()` setter alongside `changeSalaryTo()` | the setter is the path of least resistance, and it silently skips the audit record | setter removed; "pay cannot change without an audit record" written into `CLAUDE.md`, and a reviewer rule that flags any new mutator on `Employee` |
| 2 | `double` for a `what-if` percentage calculation | precision loss on aggregate money | banned `double`/`float` in the domain via a Checkstyle rule, not just a review comment |
| 3 | Loading all employees to compute a department average in Java | O(n) memory and 10,000 rows over the wire per card | added the "aggregate in SQL" rule to `CLAUDE.md`; the statement-count and `EXPLAIN` tests now catch it automatically |
| 4 | Tests asserting mock interactions rather than outcomes | coverage without confidence | `test-auditor` subagent added to the loop |
| 5 | `@Transactional` on a read-only analytics service with a write-capable datasource | subtle, harmless-looking, wrong | ArchUnit rule forbidding write ports in `analytics` |
| 6 | Reintroducing an `effective_from` column while writing the revision migration — pattern-matching on "audit table" | out of scope, and would have quietly restarted the temporal design the customer declined | scope boundaries written into `CLAUDE.md`; the reviewer subagent now flags effective-dating in a diff |
| 7 | A commit message asserting that all eight ArchUnit rules "were proved against deliberate violations" | two were. The probes — a Spring import in `domain` and a `Repository` interface declared in an adapter — could only exercise two rules. The other six govern packages that do not exist yet, so no violating class could be written to prove them. The sentence was accurate about what had been run and wrong about what it demonstrated | caught in review, not by a test — which is the point. `PLAN.md` 2.15 sets `failOnEmptyShould=true` once the packages exist, so a rule matching nothing fails from then on instead of passing quietly |
| 8 | A commit message claiming a misconfiguration "passed every test in the suite; only asserting `current_user` caught it" | three of six tests failed on that run, both refusal tests among them. **The overstatement then propagated: it was read, believed, and repeated back as fact by the reviewer — described as the project's strongest guardrail evidence and marked for use as talk material — before the agent that wrote it caught the error.** That is the real cost of the species: a wrong claim about verification does not sit still in a commit message, it becomes something someone else asserts | corrected in `DECISIONS.md` D102 with the failing output quoted. The narrow technical finding stands on its own without inflation: `@ConditionalOnMissingBean(DataSource.class)` is conditional on the type rather than the qualifier, so declaring a Flyway pool silently deletes the application's |
| 9 | `SchemaGrantsIT` drafted against `information_schema.tables` | it lists only tables the current user holds some privilege on, so an ungranted table is invisible to the role being checked — the gate would have reported success while looking at nothing, and would have passed for exactly the tables it existed to catch | enumerate from `pg_tables` and check with `has_table_privilege`, both of which see the whole schema. The cleanest example of the vacuous-gate species this project has: the obvious implementation is wrong in the direction of passing |
| 10 | A security test asserting `401` for an unauthenticated request | it passed with `permitAll` on the route, because `@PreAuthorize` on the use case refused the anonymous caller and that refusal is also a `401`. The system was safe and the test proving it was worthless - the most dangerous shape, because a correct second layer of defence hid the first one being gone | assert something only the chain can produce: `?limit=abc` fails parameter binding in the controller, so a closed chain answers `401` and an open one answers `400`. Found only because a deliberate break was required before the task could be called done (D114, D118) |


Five of the first six are now enforced by a rule or a test rather than by remembering. That is the
actual lesson: when an AI makes a mistake twice, the fix is not a better prompt, it is a build
failure.

Number 7 is a different species and the more uncomfortable one. The first six are wrong code, which
a test catches. That one was a wrong *claim about verification* in a commit message — confident,
specific, and checkable only by rerunning the thing it described. An agent that overstates what it
proved is harder to defend against than one that writes a bug, because the artefact looks like
evidence. The countermeasure is not trust: it is making the gate itself fail loudly when it has
nothing to check.

Number 6 is the one worth dwelling on. The model had read the original temporal design in the
repository's history and pattern-matched "append-only audit table" back onto it. Committed
documentation is context an agent will act on, so a superseded design left lying around is not
inert — which is why `CLAUDE.md` states the scope boundary explicitly and the reviewer subagent
checks diffs for it, rather than relying on the superseded ADR's status line to be read.
