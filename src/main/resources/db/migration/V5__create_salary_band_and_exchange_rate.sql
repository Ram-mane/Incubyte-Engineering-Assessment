-- Reference data: what a role should pay in a market, and what one currency is worth in another.
--
-- Both are read by the application and written by nobody. The seed loads them over its own
-- privileged pool (D103), so the runtime grant below says SELECT and means it: widening it so a
-- loading job could fit would make "read-only reference data" mean "read-only except for the job
-- that writes it".

-- A band is found by the natural key of role, level and market. It carries no id in the domain
-- (D083), and it carries none here either: there is nothing to reference it by, and a surrogate
-- would be a second way to name a row that already has a unique name.
CREATE TABLE salary_band (
    job_title       varchar NOT NULL,
    seniority_level varchar NOT NULL,
    country_code    char(2) NOT NULL,

    min_amount      numeric(19, 4) NOT NULL,
    mid_amount      numeric(19, 4) NOT NULL,
    max_amount      numeric(19, 4) NOT NULL,
    currency_code   char(3)        NOT NULL,

    PRIMARY KEY (job_title, seniority_level, country_code),

    -- I10, exactly as SalaryBand enforces it: 0 < min <= mid <= max, all one currency. The
    -- currency is one column for all three amounts, so "all one currency" is structural here
    -- rather than a constraint that could be violated.
    CONSTRAINT salary_band_bounds_are_ordered
        CHECK (min_amount > 0 AND min_amount <= mid_amount AND mid_amount <= max_amount),
    CONSTRAINT salary_band_level_is_known
        CHECK (seniority_level IN ('JUNIOR', 'MID', 'SENIOR', 'LEAD', 'PRINCIPAL'))
);

GRANT SELECT ON salary_band TO salary_app;

-- Dated rates, because a rate without a date is a number nobody can reproduce a total from. The
-- dashboard picks one date and normalises everything through it, so an aggregate computed today
-- and the same aggregate computed next week are comparable statements rather than coincidences.
CREATE TABLE exchange_rate (
    from_currency char(3) NOT NULL,
    to_currency   char(3) NOT NULL,
    as_of         date    NOT NULL,
    rate          numeric(19, 8) NOT NULL,

    PRIMARY KEY (from_currency, to_currency, as_of),

    -- A zero or negative rate would silently zero a payroll total rather than fail.
    CONSTRAINT exchange_rate_is_positive CHECK (rate > 0),
    -- A self-rate is not conversion and must not be stored: Money.convertTo returns identity for
    -- it without a rate, and a row saying USD->USD is 0.98 would be a total nobody could explain.
    CONSTRAINT exchange_rate_crosses_currencies CHECK (from_currency <> to_currency)
);

GRANT SELECT ON exchange_rate TO salary_app;

-- The dashboard reads one date's rates for one target currency, per aggregate.
CREATE INDEX ix_fx_lookup ON exchange_rate (to_currency, as_of, from_currency);

-- The rates themselves are seeded here rather than by the seed generator. They are five fixed rows
-- of reference data, identical in every environment, and a migration is what that is for: it means
-- the running application needs no INSERT on this table in any profile, so the SELECT-only grant
-- above is the whole truth rather than the truth outside seeding (D103, D132).
--
-- One date, because an aggregate normalised through "whatever rate was current" is a number nobody
-- can reproduce. The dashboard picks a date and says so.
INSERT INTO exchange_rate (from_currency, to_currency, as_of, rate) VALUES
    ('INR', 'USD', DATE '2026-09-01', 0.01190000),
    ('EUR', 'USD', DATE '2026-09-01', 1.08500000),
    ('GBP', 'USD', DATE '2026-09-01', 1.27300000),
    ('SGD', 'USD', DATE '2026-09-01', 0.74200000),
    ('AUD', 'USD', DATE '2026-09-01', 0.65800000);
