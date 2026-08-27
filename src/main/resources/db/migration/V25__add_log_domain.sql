-- Store the domain/hostname from the Host header so audit logs can be
-- filtered by which domain was used to access the service.

ALTER TABLE log_entries
    ADD COLUMN domain VARCHAR(255);

CREATE INDEX idx_log_entries_domain ON log_entries (domain);
