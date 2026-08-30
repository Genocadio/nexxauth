-- Opaque hash that changes on every non-password user mutation.
-- External APIs compare this to the JWT claim to detect stale data.
ALTER TABLE organisation_users
    ADD COLUMN data_hash VARCHAR(36) NOT NULL DEFAULT (gen_random_uuid()::text);
