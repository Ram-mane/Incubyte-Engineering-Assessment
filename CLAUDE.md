# CompensationIQ — working agreement

Salary management and compensation analytics for an HR Manager. ~10,000 employees, multiple countries.
Java 21 / Spring Boot 3.3 / PostgreSQL 16 / Angular 19.

**Read before writing any code:** `docs/01-REQUIREMENTS.md`, `docs/02-DOMAIN-MODEL.md`,
`docs/03-ARCHITECTURE.md`. The current step is in `PLAN.md`.

---

## The one rule that explains all the others

**Pay cannot change without an audit record.** `Employee.changeSalaryTo(Money, ChangeReason, UserId)`
is the only way a salary changes; it mutates the current value **and returns a `SalaryRevision`** to
be persisted in the same transaction. There is no setter on `currentSalary`, and `salary_revision`
is insert-only — the app's DB role has no `UPDATE` or `DELETE` on it. If you are about to write code
that changes pay without producing a revision, it is wrong however well it compiles.

**Scope note:** we deliberately do NOT have effective-dated history, validity intervals, as-of
queries, retroactive corrections or future-dating. The customer confirmed current salary is
sufficient (see `docs/adr/0002-current-salary-with-audit-log.md`). Do not reintroduce them.

---

## Architecture rules (ArchUnit enforces these — violations fail the build)

1. `..domain..` imports nothing from Spring, JPA, Jackson, or any framework. Pure Java + `shared`.
2. `..application..` depends on `domain` and on port interfaces only. Never on an adapter.
3. Ports are declared in `application/port/{in,out}`, **not** in the adapter that implements them.
4. Adapters depend inward. Nothing depends on an adapter.
5. Modules (`employee`, `compensation`, `band`, `analytics`, `bulkimport`) talk only through each
   other's inbound ports. No reaching into another module's domain or persistence.
6. No cycles between modules.
7. `analytics` is read-only — it may not reference a write port or a repository that mutates.

## Non-negotiables

- **No `double` or `float` anywhere money is involved.** `Money` (BigDecimal + CurrencyCode) only.
  Adding different currencies throws.
- **No `LocalDate.now()` / `Instant.now()` in domain or application code.** Inject `Clock`. This is
  what makes temporal behaviour testable.
- **Salaries are stored in the employee's local currency.** Aggregates are normalised to the
  reporting currency through the seeded FX table, explicitly, at query time. There is no implicit
  conversion and no "default currency" anywhere.
- **Aggregate in SQL, never in Java.** If you are about to load a list of entities to sum, average or
  group them, stop and write a query returning a projection record.
- **No N+1.** Integration tests count statements and fail if the count exceeds what the test declares.
- **Never `PATCH` a salary.** `PATCH /employees/{id}` handles profile fields only; pay changes go
  through `PUT /employees/{id}/salary` with a mandatory reason.
- **Parameterised queries only**, including hand-written analytics SQL.
- **No salary figures in log output.**

## Workflow — test first, one behaviour at a time

1. Write (or confirm) a failing test that names the *behaviour*:
   `changing_salary_to_the_same_amount_is_rejected_as_a_no_op`, not `testChangeSalary2`.
2. Make it pass with the smallest change. Touch only the files the task names.
3. Refactor with the test green.
4. Run `/review` before committing.
5. Record decisions with `/decide` as they are made. The log is append-only — supersede, never
   edit.
6. One conventional commit per logical change: `feat:`, `fix:`, `test:`, `refactor:`, `perf:`,
   `docs:`, `chore:`, `ci:`.

Never write an implementation and its test in the same breath and declare it done. Never modify a test
to make an implementation pass — if the test is wrong, say so and stop.

## Definition of done

- [ ] Test exists, names a behaviour, fails without the change
- [ ] `mvn verify` green: unit, integration, ArchUnit, Spotless, Checkstyle
- [ ] No new N+1; no new `double`; no new `now()` in domain/application
- [ ] Public API change reflected in `openapi.yaml`
- [ ] Architectural decision recorded as an ADR if one was made
- [ ] Committed with a conventional message describing *why*, not *what*

## Commands

```bash
mvn verify                      # full gate: tests, archunit, coverage, static analysis
mvn test -Dtest=EmployeeSalaryChangeTest
mvn -Pmutation test             # pitest, threshold 70% on domain
mvn spring-boot:run -Dspring-boot.run.profiles=local
mvn spring-boot:run -Dspring-boot.run.profiles=seed   # 10k employees, ~30k revisions
docker compose up -d postgres
cd ui && npm start / npm test / npm run e2e
k6 run perf/k6/directory-browse.js
k6 run perf/k6/dashboard.js
```

## Where things live

```
src/main/java/com/acme/salarymanagement/
  shared/                     Money, CountryCode, CurrencyCode, ExchangeRate
  employee/ compensation/ band/ analytics/ bulkimport/ identity/
    # compensation = change-salary use case + append-only revision log
    # analytics    = dashboard KPI cards, breakdowns, distributions (read-only)
    domain/ application/{port/in,port/out,service}/ adapter/{in/web,out/persistence}/
  config/
src/main/resources/db/migration/    Flyway, forward-only
src/test/java/.../architecture/     ArchUnit rules
ui/src/app/{core,shared,features}/
docs/                               requirements, ADRs, perf reports
perf/k6/
```

## Style

- Java records for value objects, DTOs and projections. Constructor injection, no field injection.
- No Lombok — records and a few hand-written builders cover it, and generated code hides intent.
- Package-private by default; `public` is a deliberate act.
- Guard clauses over nested conditionals. Methods short enough to see whole.
- Comments explain *why*. Names explain *what*. If a comment explains what, rename instead.
- Angular: standalone components, signals, one facade per feature, strict templates, no `any`.
  Components never inject `HttpClient` directly.

## When you are unsure

Say so and ask. A wrong assumption about compensation semantics is more expensive than a question.
Do not invent requirements — `docs/CLARIFICATIONS.md` is where open questions live.
