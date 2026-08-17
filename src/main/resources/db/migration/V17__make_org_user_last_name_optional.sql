-- Last name is now optional for organisation users (first name stays required).
-- Existing rows keep their stored value; new rows may omit it.
ALTER TABLE organisation_users ALTER COLUMN last_name DROP NOT NULL;
