---
description: Day 1 morning — walking skeleton, quality gates, CI, deploy (tasks 1.1-1.6)
---

Execute **PLAN.md tasks 1.1 through 1.6**, one commit per task. Stop after 1.6.

Do them **one at a time**. After each, show me the diff and wait. Do not batch.

Start with **1.1 only**: Maven, Java 21, Spring Boot 3.3.x, jar packaging, groupId `com.acme`,
artifactId `salary-management`. Wire Spotless, Checkstyle, PMD, JaCoCo, Pitest and ArchUnit into the build
**now**, before any feature code — including the Checkstyle rule banning `double` and `float` in
domain packages. `mvn verify` must pass on the empty project. Show me the pom before anything else.

Then 1.2 Docker Compose (Postgres 16 with `pg_trgm`) + Flyway + a Testcontainers base test;
1.3 GitHub Actions; 1.4 Angular 19 standalone + Material with a dev proxy; 1.5 deploy both to
Render and put the live URL in the README; 1.6 the ArchUnit rules from `docs/03-ARCHITECTURE.md` §4.

**Gates before features is not negotiable.** A JaCoCo or Pitest threshold is easy to satisfy from
zero and painful to retrofit onto existing code. If you find yourself wanting to defer one, say so
and stop rather than deferring it silently.

After 1.6, verify the gates actually bite: introduce a `double` in a domain package and a Spring
import in `..domain..`, confirm `mvn verify` fails on each, then revert. A gate that has never
failed is decoration. Report the exact commands that broke it — they are wanted for the demo video.

Finish with `/wrap`.
