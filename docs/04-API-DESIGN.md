# API Design

REST over JSON, `/api/v1`. Contract-first: `openapi.yaml` is written before controllers and is the
source of truth; Springdoc verifies the running app matches it.

## Conventions

- Resources are nouns, plural. Actions that are not CRUD are sub-resources (`/salary`,
  `/salary-revisions`), never verbs in the path.
- **Money is always `{ "amount": "1200000.00", "currency": "INR" }`** — amount as a *string* so no
  JavaScript client can silently lose precision on a large number. Never a bare float.
- Dates are ISO-8601 `YYYY-MM-DD`; timestamps are RFC 3339 UTC.
- Errors are RFC 7807 `application/problem+json`.
- Collections return `{ "items": [...], "nextCursor": "...", "totalApprox": 10000 }`.
- Writes require `Idempotency-Key` on salary changes.
- `ETag` / `If-Match` on employee updates for optimistic concurrency.

## Endpoints

### Employees
| Method | Path | Notes |
|---|---|---|
| `GET` | `/employees` | `?q=&department=&country=&level=&status=&cursor=&limit=` — keyset paged |
| `GET` | `/employees/{id}` | includes current salary + band position |
| `POST` | `/employees` | onboard; requires an initial salary in the same command |
| `PATCH` | `/employees/{id}` | profile fields only — **never** salary |
| `POST` | `/employees/{id}/terminate` | |

Salary is deliberately not writable through the employee resource. A pay change carries a mandatory
reason and must produce an audit entry; allowing `PATCH /employees/1 {"salary": ...}` alongside the
other profile fields would make it possible to change pay silently. The API shape enforces the
domain rule.

### Compensation
| Method | Path | Notes |
|---|---|---|
| `PUT` | `/employees/{id}/salary` | `{amount, currency, reason, note}` → 200 with the updated employee |
| `GET` | `/employees/{id}/salary-revisions` | the audit log, newest first, paged |

`PUT /salary` returns `422` if the amount is not positive, the currency does not match the
employee's country, the reason is missing, or the employee is terminated; and `409` if the submitted
value is identical to the current one (I6 — a no-op change should not write an audit entry).

### Bands
| Method | Path |
|---|---|
| `GET` | `/bands?jobTitle=&level=&country=` |
| `POST` / `PUT` | `/bands` |

### Dashboard — the reason the product exists
Every endpoint accepts the same filter set: `country`, `department`, `jobTitle`, `level`, `status`.
Aggregates are normalised to `currency` (default USD) using the seeded FX table.

| Method | Path | Answers |
|---|---|---|
| `GET` | `/dashboard/summary` | the KPI cards — total payroll spend, active headcount, average salary, median salary — in one round trip |
| `GET` | `/dashboard/breakdown?groupBy=department\|country\|level` | spend and headcount per group |
| `GET` | `/dashboard/distribution?groupBy=jobTitle\|department` | p25 / median / p75 / p90 and range per group |
| `GET` | `/dashboard/band-positions?cursor=` | each employee's compa-ratio, sortable — displayed, not enforced |

`/dashboard/summary` is one query, not four. Four cards driven by four round trips is the kind of
thing that looks fine on a demo and is a visible stutter on a filter change.

### Bulk import
| Method | Path | Notes |
|---|---|---|
| `POST` | `/imports` | multipart CSV → `202 Accepted` + job id |
| `GET` | `/imports/{id}` | status, counts, and a downloadable rejected-rows CSV with per-row reasons |

Streamed and chunked; a 10,000-row file never sits in memory as objects. Partial success is the
default: valid rows land, invalid rows come back annotated, because an HR manager migrating off
Excel needs to fix 12 bad rows, not re-upload 10,000.

## Auth

`POST /auth/login` → short-lived JWT. Roles `HR_MANAGER` (read/write) and `HR_ANALYST` (read-only),
enforced with `@PreAuthorize` at the **use-case** layer, not only at the controller — so a future
second entry point cannot bypass it. Seeded demo credentials are in the README.

## Example error

```json
{
  "type": "https://compiq.dev/errors/currency-mismatch",
  "title": "Salary currency does not match the employee's country",
  "status": 422,
  "detail": "Employee 4821 is based in IN; salary must be in INR, but EUR was submitted.",
  "instance": "/api/v1/employees/4821/salary",
  "expectedCurrency": "INR",
  "correlationId": "01J8Z..."
}
```
