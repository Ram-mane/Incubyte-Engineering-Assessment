# Test Strategy

The brief asks for tests that are **meaningful, fast, deterministic and easy to understand**. Those
four words drive every choice here. Coverage percentage is not a goal; it is a smoke detector.

## 1. The shape

```
        /\        E2E (Playwright)            ~5 specs      ~60 s
       /  \       API integration             ~35 tests     ~40 s   real Postgres
      /    \      Slice (@WebMvcTest, Angular) ~55 tests    ~10 s
     /______\     Unit — pure domain          ~150 tests    < 2 s   no Spring, no DB
```

Most of the value is at the bottom, because most of the *risk* is at the bottom: currency
arithmetic, FX normalisation and compa-ratio classification are where a bug silently reports the
wrong number to someone making a pay decision.

**Target: whole suite under 90 seconds.** A suite developers wait for is a suite developers skip.

## 2. Unit tests — the ones that matter

`Employee`, `Money`, `BandPosition` and the FX normaliser are plain Java with no framework. Their
tests are plain JUnit with no context. Examples:

```java
@Test void changing_salary_records_the_previous_amount_on_the_revision() { ... }
@Test void changing_salary_to_the_same_amount_is_rejected_as_a_no_op() { ... }
@Test void salary_currency_must_match_the_employee_country() { ... }
@Test void terminated_employee_rejects_a_salary_change() { ... }
@Test void adding_inr_to_eur_is_rejected() { ... }
@Test void converting_to_usd_uses_the_rate_for_the_requested_date() { ... }
@Test void compa_ratio_is_undefined_when_no_band_matches() { ... }
@Test void rounding_a_jpy_amount_keeps_zero_decimal_places() { ... }
```

Every invariant I1–I10 in [02-DOMAIN-MODEL.md](02-DOMAIN-MODEL.md) has at least one test named after
the behaviour, not the method. `should_return_true` tells a reviewer nothing;
`changing_salary_to_the_same_amount_is_rejected_as_a_no_op` tells them the whole rule.

**Property-based tests** (jqwik) for money and FX, where the edge cases are combinatorial:
> for any sequence of salary changes, the number of audit revisions equals the number of accepted
> changes, and each revision's `previousAmount` equals the preceding revision's `newAmount`.

> for any set of salaries in any mix of currencies, converting each to USD and summing equals
> summing per-currency and then converting — within one minor unit.

The second one catches rounding-accumulation bugs in the KPI totals that no example-based test
would, and it is the kind of thing worth showing in an interview.

## 3. Integration tests

- **Testcontainers PostgreSQL**, one reused container per suite (`.withReuse(true)`), Flyway migrations
  applied — so tests run against the same engine, dialect and constraints as production.
- Every dashboard native query has a test asserting both its result *and* that it uses an index scan,
  by parsing `EXPLAIN` output. A query that silently becomes a sequential scan fails the build.
  Incubyte asked to see indexing and query performance demonstrated; this is how it is demonstrated
  rather than asserted.
- **N+1 guard**: a JUnit extension counts statements per request and fails if the count exceeds the
  number the test declares. This catches the most common Spring performance regression automatically.
- A test asserts the application role has no `UPDATE` or `DELETE` grant on `salary_revision`, so the
  append-only guarantee is enforced by the database and not only by the repository.

## 4. Architecture tests (ArchUnit)

Rules from [03-ARCHITECTURE.md](03-ARCHITECTURE.md) §4, executable:

```java
@ArchTest static final ArchRule domain_is_framework_free =
    noClasses().that().resideInAPackage("..domain..")
      .should().dependOnClassesThat()
      .resideInAnyPackage("org.springframework..", "jakarta.persistence..", "com.fasterxml..");

@ArchTest static final ArchRule modules_have_no_cycles =
    slices().matching("com.acme.salarymanagement.(*)..").should().beFreeOfCycles();

@ArchTest static final ArchRule analytics_is_read_only =
    noClasses().that().resideInAPackage("..analytics..")
      .should().dependOnClassesThat().haveSimpleNameEndingWith("WriteRepository");
```

This is what makes the architecture real rather than aspirational.

## 5. Mutation testing

Pitest on `..domain..` and `..application..`, **threshold 70%, build fails below it.**

Line coverage says a line ran. Mutation coverage says that if the line were wrong, a test would
notice. For a system that computes people's pay, that distinction is the whole point — and it is a
direct answer to the brief's "meaningful set of unit tests".

JaCoCo runs too, gated at 80% on domain/application and *ungated* on adapters, because chasing
coverage on generated mappers produces tests nobody should read.

## 6. Frontend

- **Jest + Angular Testing Library.** Tests query by role and label, never by CSS class, so a
  restyle does not break them.
- Facades tested against a mocked `HttpClient`; components tested against a stub facade.
- **MSW** for realistic API fixtures shared with the E2E suite.
- **Playwright**, five specs only, covering the journeys that must never break:
  1. log in → directory loads → filter by department
  2. open an employee → view salary timeline
  3. change a salary → employee view updates → a revision appears in the audit log
  4. dashboard loads, KPI cards populate, applying a country filter updates every card and chart
  5. CSV import with two bad rows → rejected-rows report downloads

## 7. Determinism

Non-negotiable, and mostly a matter of banning three things:

- **No `LocalDate.now()` / `Instant.now()` in domain or application code.** A `Clock` is injected
  everywhere; tests use `Clock.fixed`. Audit timestamps and FX rate dates both depend on it.
- **No random data in assertions.** The seeder uses a fixed RNG seed; test builders use explicit values.
- **No `Thread.sleep`, no shared mutable state between tests, no ordering dependencies.** Tests run in
  parallel; a suite that only passes serially is hiding a bug.

## 8. Test data

Object Mother + fluent builders:

```java
Employee alice = anEmployee().inIndia().asSeniorEngineer()
    .hiredOn("2019-04-01").earning("1,200,000 INR").build();

alice.changeSalaryTo(money("1,380,000 INR"), MERIT, hrManager);
```

The test reads as the scenario. A reviewer understands it without reading the setup.

## 9. CI gates

GitHub Actions on every push. The build fails on any of:
unit or integration failure · ArchUnit violation · mutation score < 70% ·
JaCoCo < 80% on domain/application · Spotless formatting drift · Checkstyle/PMD error ·
`npm run lint` error · Playwright failure · OWASP dependency-check high severity.
