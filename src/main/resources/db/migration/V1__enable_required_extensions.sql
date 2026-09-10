-- pg_trgm backs the trigram index behind employee name search (docs/05-DATA-MODEL.md).
-- It is created by a migration rather than by hand so that every environment - a developer's
-- Docker Compose, the Testcontainers database in CI, and the deployed instance - is provisioned
-- identically. An extension that exists only where someone remembered to add it is a
-- production-only failure waiting to happen.
CREATE EXTENSION IF NOT EXISTS pg_trgm;
