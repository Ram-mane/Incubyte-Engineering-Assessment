# Architecture

## 1. Shape: modular monolith, hexagonal inside each module

One deployable Spring Boot artifact. Inside it, four business modules with enforced boundaries,
each structured as ports-and-adapters.

**Why not microservices.** The assessment asks for good engineering judgment, and the judgment here is
that a single team building one product for one org with a 120k-row dataset gets nothing from network
boundaries except latency, distributed transactions and an ops burden. What we *do* need is the
ability to split later if the org grows — which is what module boundaries buy, at a fraction of the
cost. [ADR-0001](adr/0001-modular-monolith.md) records this in full, including the trigger conditions
that would justify splitting.

## 2. C4 Level 1 — Context

```mermaid
graph TB
    HRM["HR Manager<br/><i>manages pay, answers exec questions</i>"]
    HRA["HR Analyst<br/><i>read-only</i>"]
    SYS["CompensationIQ<br/><b>Salary management &amp; analytics</b>"]
    CSV["Legacy spreadsheets<br/><i>CSV export</i>"]
    FX["FX rate source<br/><i>seeded; port for live feed</i>"]

    HRM -->|"HTTPS / Angular SPA"| SYS
    HRA -->|"HTTPS / Angular SPA"| SYS
    CSV -->|"bulk import"| SYS
    FX -.->|"ExchangeRateProvider port"| SYS
```

## 3. C4 Level 2 — Containers

```mermaid
graph TB
    subgraph Browser
      SPA["Angular 19 SPA<br/>standalone components, signals<br/>Angular Material"]
    end
    subgraph "Render.com"
      API["Spring Boot 3.3 / Java 21<br/>REST + JWT<br/>modular monolith"]
    end
    subgraph "Neon"
      PG[("PostgreSQL 16<br/>Flyway-migrated")]
    end
    CACHE["Caffeine in-process cache<br/>bands, FX, analytics"]

    SPA -->|"JSON over HTTPS"| API
    API --> PG
    API --> CACHE
```

Deliberately **no Redis, no message broker, no separate worker** in v1. Each would be a container to
run, secure and explain, for a dataset that fits comfortably in one process's cache.
[06-SCALABILITY.md](06-SCALABILITY.md) states the exact thresholds at which each earns its place.

## 4. C4 Level 3 — Modules and their dependencies

```mermaid
graph LR
    WEB["adapter.in.web<br/><i>controllers, DTOs</i>"]
    subgraph Modules
      EMP["employee"]
      COMP["compensation<br/><i>salary change + audit log</i>"]
      BAND["band"]
      ANA["analytics<br/><i>dashboard, read-only</i>"]
      IMP["bulkimport"]
    end
    IDN["identity<br/><i>auth, users, roles</i>"]
    SHR["shared-kernel<br/><i>Money, CountryCode, ExchangeRate</i>"]
    PERS["adapter.out.persistence<br/><i>JdbcTemplate: projections and explicit writes</i>"]

    WEB --> EMP & COMP & BAND & ANA & IMP
    COMP --> EMP
    ANA --> COMP & BAND & EMP
    IMP --> EMP & COMP
    EMP & COMP & BAND & ANA & IMP --> SHR
    EMP & COMP & BAND & ANA --> PERS
    WEB --> IDN
```

Rules, all enforced by ArchUnit tests in `ArchitectureTest.java`:

1. `domain` **and `shared-kernel`** depend on nothing but each other and the JDK. Stated as an
   allowlist, not a list of banned frameworks: naming Spring, JPA and Jackson only bans the ones
   someone thought of, and slf4j or the next convenient library would pass. `shared` is inside
   the boundary because the domain depends on it — a framework reaching `shared` reaches the
   domain with it.
2. `application` depends on `domain` and on **ports** only — never on an adapter.
3. Adapters depend inward. Nothing depends on an adapter.
4. Modules talk to each other only through the other module's `application` port interfaces,
   never by reaching into its `domain` or `persistence`.
5. No cycles between modules.
6. `analytics` is read-only: it may not depend on any write port.

A build that violates these fails. That is the difference between an architecture and a diagram.

## 5. Package layout

```
com.acme.salarymanagement
├── shared/                       # Money, CountryCode, CurrencyCode, ExchangeRate, DomainException
├── employee/
│   ├── domain/                   # Employee, EmployeeNumber, EmploymentStatus
│   ├── application/
│   │   ├── port/in/              # OnboardEmployeeUseCase, SearchEmployeesQuery
│   │   ├── port/out/             # EmployeeRepository (interface, defined HERE not in persistence)
│   │   └── service/              # implementations
│   └── adapter/
│       ├── in/web/               # EmployeeController, request/response records
│       └── out/persistence/      # EmployeeJdbcAdapter, EmployeeDirectoryJdbcAdapter (projections)
├── compensation/                 # salary change use case + append-only revision log
├── band/
├── analytics/                    # read-only dashboard; native SQL projections, no entities
├── bulkimport/
├── identity/
└── config/                       # Spring wiring, SecurityConfig, CacheConfig, OpenApiConfig
```

The **ports are owned by the application layer**, not by the adapter. `EmployeeRepository` is an
interface in `employee/application/port/out/` that the persistence adapter implements. That
inversion is what makes the domain testable without a database, and it's the detail most
"hexagonal" codebases get backwards.

## 6. Request flow, end to end

```mermaid
sequenceDiagram
    participant UI as Angular
    participant C as SalaryController
    participant UC as ChangeSalaryService
    participant E as Employee (domain)
    participant R as EmployeeRepository (port)
    participant RL as RevisionLog (port)
    participant DB as PostgreSQL

    UI->>C: PUT /api/v1/employees/{id}/salary
    C->>C: validate DTO, map to command
    C->>UC: change(command)
    UC->>R: load(employeeId)
    R->>DB: SELECT
    DB-->>R: row
    R-->>UC: Employee
    UC->>E: changeSalaryTo(money, reason, actor)
    Note over E: pure domain —<br/>validates I1-I9,<br/>mutates currentSalary AND<br/>returns the SalaryRevision
    E-->>UC: SalaryRevision
    UC->>R: save(employee)
    UC->>RL: append(revision)
    RL->>DB: UPDATE employee + INSERT revision (one tx)
    UC->>UC: publish SalaryChanged
    UC-->>C: EmployeeView
    C-->>UI: 200 OK
```

Note what the controller does *not* do: no business rules, no repository calls, no entity handling.
And note that `Employee.changeSalaryTo()` — where the rule lives — takes no dependencies and can be
tested with a plain JUnit test in microseconds. It returns the `SalaryRevision` rather than writing
it, so the audit guarantee is a property of the domain object, not of remembering to call something.

## 7. Read model vs write model

**Every adapter uses `JdbcTemplate`. There are no JPA entities.** Reads return flat projection
records of exactly the columns a screen shows; writes are explicit statements inside one
transaction.

Reads are SQL for the reason ADR-0005 gave and it still holds: the KPI cards aggregate 10,000 rows
across six currencies with FX conversion and the distributions need `percentile_disc` — `_disc` and
not `_cont` wherever the value is money, because interpolating leaves `numeric` for
`double precision` (D135) — which is a SQL problem rather than an object-graph problem. Loading
entities to sum them in Java is the single most common performance mistake in Spring applications.

Writes are explicit statements for a different and stronger reason: **dirty checking is an ambient
write path**. A managed `Employee` whose salary changed flushes an `UPDATE` at commit with no
`SalaryRevision`, and neither the ArchUnit rule nor the append-only grant can see it — the rule
forbids constructing a revision outside the domain, not omitting one, and the grant restricts
`salary_revision` while the application legitimately holds `UPDATE` on `employee`. The absent
setter, the returned revision and the restricted role all exist to make an unrecorded pay change
impossible to express; an ORM that writes fields because they changed would make it the default.

[ADR-0014](adr/0014-jdbctemplate-everywhere-jpa-on-a-read-path.md) records this and supersedes the
write half of [ADR-0005](adr/0005-native-sql-for-analytics.md). The JPA query-performance criterion
the brief asks for is met on a **read** path after the dashboard ships: one mapped read, written to
produce an N+1, caught by the statement-count harness and fixed with an entity graph, with the
before and after counts committed as evidence.

## 8. Frontend architecture (Angular 19)

```
src/app/
├── core/          # auth interceptor, error interceptor, api base, guards
├── shared/        # money pipe, band-position chip, data-table, empty/error states
└── features/
    ├── employees/     # list (virtual scroll + server pagination), detail, revision log
    ├── compensation/  # change-salary dialog
    ├── bands/
    └── dashboard/     # KPI cards, filters, charts — the product's differentiator
```

- **Standalone components** throughout; no NgModules.
- **Signals** for component state; `httpResource`/`toSignal` for server state. No NgRx —
  this app has little cross-cutting client state and a store would be ceremony.
  ([ADR-0008](adr/0008-signals-over-ngrx.md).)
- One **facade service per feature**: components never call `HttpClient` directly.
- Strict TypeScript, `strictTemplates`, no `any`.
- Currency and dates formatted through shared pipes so nothing is hand-concatenated.

## 9. Cross-cutting

| Concern | Approach |
|---|---|
| Errors | RFC 7807 `application/problem+json` via `@RestControllerAdvice`. Domain exceptions map to 4xx; everything else is a 500 with a correlation ID and no internals leaked. |
| Validation | Bean Validation at the DTO edge for shape; domain invariants inside the domain. Two different jobs, deliberately not merged. |
| Observability | Micrometer + Actuator, `/actuator/health` and `/prometheus`. Structured JSON logs with a per-request correlation ID. Timers on every use case. |
| Config | 12-factor. No secrets in the repo. Profiles: `local`, `test`, `prod`. |
| API versioning | `/api/v1` from day one. |
| Migrations | Flyway, forward-only, every change reviewable as SQL. |
