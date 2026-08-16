-- Organisation client keys: an opaque, non-guessable identifier that external
-- apps send as X-Client-Id, replacing the guessable numeric auto-increment id.
-- The column stays nullable in the schema so rows created before this migration
-- can be backfilled by the application on startup (OrganisationClientKeyBackfill)
-- while this script stays portable across H2 (tests) and PostgreSQL (prod).

ALTER TABLE organisation_clients ADD COLUMN client_key VARCHAR(64);

ALTER TABLE organisation_clients ADD CONSTRAINT uk_organisation_clients_client_key UNIQUE (client_key);