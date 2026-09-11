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

> **P1, exact.** Summing salaries within one currency and converting the subtotal once is
> invariant under reordering, and equals a full-precision reference rounded once — which is what
> PostgreSQL's `sum(amount * rate)` computes. Zero tolerance.

> **P2, bounded.** Converting each salary and *then* summing drifts from P1 by at most
> `0.005 × (n + currencies)`. It rounds `n` times where P1 rounds once per currency.

The second is named `per_row_conversion_accumulates_error_and_must_not_be_used_for_totals`, because
it documents a prohibition rather than permitting the drift — see [ADR-0013](adr/0013-sum-then-convert-for-currency-totals.md).
An earlier draft of this document stated the bound as "within one minor unit". That is
arithmetically false for any list long enough to expose it, and a property written to it would have
passed only because it was kept small.

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

Pitest on `..shared..`, `..domain..` and `..application..`, **threshold 70%, build fails below it.**
`shared` is in scope because that is where `Money` and the FX arithmetic live: scoping the gate to
domain and application alone left the code that computes people's pay unmutated, and the threshold
passed on zero mutations.

**A mutation score is bounded by what the tool elects to mutate, and ours does not mutate
everything.** Read the number with that in mind — the README quotes it.

- Pitest's record filter strips mutations from a record's compact constructor along with its
  generated `equals`/`hashCode`/`toString`. Our validation lives in compact constructors, so with
  the filter on `ExchangeRate` generated **zero** mutations while the gate reported 100%. The build
  now runs `-FRECORD`, which surfaces the real guards at the cost of uncovered mutations in
  generated methods nobody writes tests for.
- Even with the filter off, coverage of a constructor exercised from a **test class's static
  initialiser** is not attributed to individual tests, so Pitest reports `SURVIVED` for mutations
  that are in fact killed. `CurrencyCode`'s `< 0` guard is one: Pitest calls it survived, and
  changing it by hand fails 34 tests.
- `Money`'s compact constructor holds the rounding policy — `setScale(currency.scale(),
  HALF_EVEN)` — and **no default mutator applies to it**: there is no conditional, no arithmetic
  operator and no return value to mutate. It cannot be covered by this gate at all.

That last one is verified by hand instead, and re-run whenever the policy changes:

| Manual probe | Result |
|---|---|
| `HALF_EVEN` → `HALF_UP` | `MoneyTest$Rounding` fails 3 of 4 — `a_half_cent_rounds_down_when_the_preceding_digit_is_even` first |
| `currency.scale()` → `currency.scale() + 1` | `MoneyTest$Scale`, `$Equality` and `$Conversion` all fail |
| `Employee.equals` → always `true` | `two_employees_with_different_identities_are_different_employees` fails |
| `Employee.equals` → always `false` | `two_employees_with_the_same_identity_are_the_same_employee` fails |

The last two exist because `equals`, `hashCode` and `toString` are excluded from mutation by name
(D074) — Pitest cannot scope an exclusion to generated methods, so the one hand-written `equals` in
the domain is excluded with them. Identity equality decides whether an aggregate is the same
aggregate after a change, so it is verified by hand rather than left uncovered.

Two further blind spots, found by auditing the tests rather than reading the score:

- **The score says nothing about any null guard in this codebase.** `Objects.requireNonNull(Object,
  String)` returns a value, so Pitest's `VOID_METHOD_CALLS` mutator does not remove it. Deleting a
  `requireNonNull` is not a mutation any default mutator performs — every null guard is covered by
  example tests or not at all.
- **The score says nothing about `Money`'s arithmetic.** `BigDecimal.add`, `subtract` and `multiply`
  are method calls rather than bytecode operators, so no MATH mutator applies to a single line of
  `Money`. The arithmetic rests entirely on the example tests, which is fine because they are
  thorough — but it is not the mutation score that is protecting it.

The rounding probes above also under-report what the tests do. `MoneyTest$Rounding` and `$Scale`
together kill **all seven** alternative rounding modes deterministically, not only `HALF_UP`:
`2.005 → 2.00` kills UP, CEILING and HALF_UP; `2.015 → 2.02` kills DOWN, FLOOR and HALF_DOWN;
`1234.567 → 1234.57` kills UNNECESSARY; `-2.005 → -2.00` pins the negative-half direction.

An automated number covering part of the code, plus a recorded manual probe covering the rest, is
honest. An automated number quoted as if it covered everything is not - and the README must not
quote 90% without these caveats.

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
