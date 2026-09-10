---
description: Run the seed generator and sanity-check the dataset
---

1. `docker compose up -d postgres` and wait for health.
2. `mvn spring-boot:run -Dspring-boot.run.profiles=seed`
3. Verify with SQL and report the numbers:
   - employee count (expect 10,000)
   - salary_revision count (expect ~30,000)
   - active employees with a null or non-positive salary (**must be 0**)
   - employees whose salary currency does not match their country (**must be 0**)
   - revisions whose `previous_amount` does not match the prior revision's `new_amount` (**must be 0**)
   - distribution across departments, countries and levels — flag anything implausible
   - min/max/median current salary per country — flag anything absurd
4. Report the wall-clock time. If it exceeds 30 s, profile the batch insert before moving on.
5. Confirm re-running is idempotent and the RNG seed produces an identical dataset.
