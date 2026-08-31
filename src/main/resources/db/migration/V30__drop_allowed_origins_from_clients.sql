-- The allowed_origins column is superseded by organisation_client_links.
ALTER TABLE organisation_clients DROP COLUMN IF EXISTS allowed_origins;
