-- The audit log. One row per salary change, written in the same transaction as the change.
--
-- Employee.changeSalaryTo returns the revision rather than writing it, so no code path can move
-- pay without producing the record. That guarantee reaches exactly as far as the code does: a
-- stray UPDATE would rewrite what someone was paid and the aggregate would never know. Hence the
-- grants at the bottom of this file - the append-only property is the database's, not the
-- repository's (D020, ADR-0002).
--
-- Identity is a uuid the persistence adapter assigns, not a BIGSERIAL. This reverses the surrogate
-- key D079 anticipated: a sequence default is a database-minted identity, which is the thing the
-- rest of the schema deliberately does not have (D091). The aggregate still mints nothing - it
-- returns a record with no id - so changeSalaryTo remains a pure function of its arguments and the
-- seed generator's thirty thousand revisions stay reproducible (D094).

CREATE TABLE salary_revision (
    id              uuid    PRIMARY KEY,
    employee_id     uuid    NOT NULL REFERENCES employee (id),

    -- Both amounts in one currency column: an employee is paid in their country's currency (I9),
    -- so a change cannot cross currencies and two currency columns would imply it could.
    previous_amount numeric(19, 4) NOT NULL,
    new_amount      numeric(19, 4) NOT NULL,
    currency_code   char(3)        NOT NULL,

    change_reason   varchar     NOT NULL,
    -- Who and when. No DEFAULT on changed_at: the application resolves its Clock to an Instant at
    -- the boundary and passes it in, and a now() default here would be the same ambient time the
    -- domain refuses, one layer further down where no test can see it (D091).
    changed_by      uuid        NOT NULL REFERENCES app_user (id),
    changed_at      timestamptz NOT NULL,
    -- Optional, unlike the reason. The domain stores a blank note as no note (D081).
    note            varchar     NULL,

    -- Each mirrors an invariant the domain already enforces, and no more: the ChangeReason enum
    -- as varchar + CHECK (D078), I1 on both amounts, and I6 - a change to the same amount is a
    -- no-op the aggregate refuses, so a row recording one could only have come from a code path
    -- that went around it.
    CONSTRAINT salary_revision_reason_is_known
        CHECK (change_reason IN ('MERIT', 'PROMOTION', 'MARKET_ADJUSTMENT', 'CORRECTION')),
    CONSTRAINT salary_revision_amounts_are_positive CHECK (previous_amount > 0 AND new_amount > 0),
    CONSTRAINT salary_revision_is_a_change CHECK (new_amount <> previous_amount)
);

-- One employee's own log, newest first, which is the only way a revision is ever read
-- (GET /employees/{id}/salary-revisions at 2.7). id is in the index because it is the keyset
-- tiebreaker: changed_at alone is not a total order, and two changes in the same instant would
-- otherwise duplicate or skip a row across pages.
CREATE INDEX ix_revision_employee ON salary_revision (employee_id, changed_at DESC, id DESC);

-- The application's own role, introduced here because this is the first table whose privileges
-- are a domain guarantee rather than an operational detail. NOLOGIN and no password: attaching a
-- login to it is provisioning's job, and a credential does not belong in a migration. In a
-- deployed environment the connecting user is a member of this role; the integration tests reach
-- it with SET ROLE, which is also how 2.3 proves the database refuses what is not granted.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'salary_app') THEN
        CREATE ROLE salary_app NOLOGIN;
    END IF;
END
$$;

GRANT USAGE ON SCHEMA public TO salary_app;

-- Least privilege, stated as what each table is for. Pay changes in place, so employee is
-- writable; nobody deletes an employee, they are TERMINATED. Users are read to authenticate and
-- inserted by the seed.
GRANT SELECT, INSERT, UPDATE ON employee TO salary_app;
GRANT SELECT, INSERT ON app_user TO salary_app;

-- The whole point. No UPDATE and no DELETE, so a written revision cannot be altered or removed by
-- the application at all - not by a bug, not by a repository method nobody reviewed, not by a
-- console session using the application's credentials.
GRANT SELECT, INSERT ON salary_revision TO salary_app;
