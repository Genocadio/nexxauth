-- Soft deletion for organisation users.
-- Deleted accounts have deleted_at set and enabled = false.
-- They cannot log in, and are permanently purged after 30 days.
ALTER TABLE organisation_users
    ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE DEFAULT NULL;

CREATE INDEX idx_organisation_users_deleted_at
    ON organisation_users (deleted_at);
