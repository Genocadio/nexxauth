-- Fix FK constraint so deleting an organisation doesn't fail due to log entries.
-- Preserve the audit trail by setting organisation_id to NULL on delete.

ALTER TABLE log_entries DROP CONSTRAINT fk_log_entries_organisation;

ALTER TABLE log_entries
    ADD CONSTRAINT fk_log_entries_organisation
    FOREIGN KEY (organisation_id) REFERENCES organisations (id) ON DELETE SET NULL;
