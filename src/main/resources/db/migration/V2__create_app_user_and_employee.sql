-- The system of record: who can log in, and who works here for what pay.
--
-- V1 is enable_required_extensions and has already been applied everywhere, so this schema is V2
-- and every later planned migration shifts by one (PLAN.md Day 2, docs/DECISIONS.md D090).
--
-- Two rules run through every table below, and EmployeeSchemaIT asserts both (D091):
--
--   1. No column has a DEFAULT, and no table has a trigger. Identity is minted by the application
--      - EmployeeId and UserId are UUIDs assigned at construction - so a gen_random_uuid() default
--      would be a second source of identity, producing rows the domain never minted and cannot
--      recognise. The seed generator holds ten thousand employees in memory before the first
--      insert and relies on their ids already existing.
--   2. Nothing here reads the clock. The domain takes the Instant to record rather than consulting
--      a Clock, and that rule does not stop at the port boundary: a now() default or a timestamp
--      trigger would reintroduce ambient time one layer further down, where no test can see it.
--      This migration therefore has no timestamp column at all; salary_revision.changed_at (2.2)
--      is written by the adapter from the Instant it was passed.

-- Columns only. Authentication is 2.9: this exists now so the seeded users the demo logs in as
-- have somewhere to live, and so salary_revision.changed_by (2.2) has a table to reference.
CREATE TABLE app_user (
    id            uuid    PRIMARY KEY,
    email         varchar NOT NULL UNIQUE,
    password_hash varchar NOT NULL,
    -- No CHECK on role, deliberately, unlike status and seniority_level below. Those two mirror
    -- enums the domain already has; there is no Role type in the code until 2.9, and putting the
    -- authoritative list of roles in the schema first would make the database the source of truth
    -- for a domain concept.
    role          varchar NOT NULL
);

-- Column for column, the Employee aggregate: nothing here that the domain does not hold, so the
-- adapter at 2.5 maps rather than invents. Notably absent, and absent on purpose: manager_id,
-- employment_type, termination_date and version, all of which the ERD drew and the aggregate does
-- not have. A column no code writes is a column whose meaning is decided later by whoever guesses.
-- Also absent: any effective_from or valid_from. That model was superseded (ADR-0002).
CREATE TABLE employee (
    id              uuid    PRIMARY KEY,
    employee_number varchar NOT NULL UNIQUE,
    given_name      varchar NOT NULL,
    family_name     varchar NOT NULL,
    email           varchar NOT NULL UNIQUE,
    country_code    char(2) NOT NULL,
    -- A name, not a reference. Nothing in this system does anything to a department, so there is
    -- no department aggregate and no table to join: it is a filter dimension exactly like
    -- job_title and seniority_level beside it, and what the dashboard groups by is the name. An
    -- id would put a join in front of the KPI query, which reads nothing from the joined table,
    -- and in front of the directory filter, which would then be indexed on a surrogate the UI
    -- never sends. The filter dropdown is SELECT DISTINCT department, off the index below.
    department      varchar NOT NULL,
    job_title       varchar NOT NULL,
    seniority_level varchar NOT NULL,
    hire_date       date    NOT NULL,
    status          varchar NOT NULL,

    -- Exact decimal, and the currency travels beside the amount. numeric, never the PostgreSQL
    -- money type, which is locale-dependent and carries no currency of its own - a column holding
    -- "1,200,000.00" whose meaning changes with lc_monetary is not a pay figure, it is a string
    -- that looks like one. Scale 4 rather than 2 because Money's scale is per currency and the
    -- column has to hold all of them; the domain still rounds to the currency's own scale.
    salary_amount   numeric(19, 4) NOT NULL,
    salary_currency char(3)        NOT NULL,

    -- Each of these three mirrors an invariant the domain already enforces, and nothing else does:
    -- I1 on Money/Employee, and the two enums whose value sets are enforced by their types. Stored
    -- as varchar + CHECK rather than a native enum, for the reason recorded in D078: extending a
    -- CHECK is cheap and altering an enum type is a migration with locking behaviour.
    CONSTRAINT employee_salary_is_positive CHECK (salary_amount > 0),
    CONSTRAINT employee_status_is_known CHECK (status IN ('ACTIVE', 'TERMINATED')),
    CONSTRAINT employee_seniority_level_is_known
        CHECK (seniority_level IN ('JUNIOR', 'MID', 'SENIOR', 'LEAD', 'PRINCIPAL'))
);

-- The directory's filter dimensions (04-API-DESIGN: ?department=&country=&level=), indexed now
-- because their consumer is the search query at 2.6, not because a filtered column looks like it
-- wants an index. The composite covering, keyset and trigram indexes are 2.4's job: they encode
-- choices about sort key, column order and operator class that the query is what settles.
CREATE INDEX ix_employee_country ON employee (country_code);
CREATE INDEX ix_employee_department ON employee (department);
CREATE INDEX ix_employee_job_title ON employee (job_title);
CREATE INDEX ix_employee_seniority_level ON employee (seniority_level);
