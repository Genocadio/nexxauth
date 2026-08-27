-- Add client_key to log_entries so logs can be filtered by the API client
-- that initiated the request.

ALTER TABLE log_entries
    ADD COLUMN client_key VARCHAR(64);

CREATE INDEX idx_log_entries_client_key ON log_entries (client_key);
