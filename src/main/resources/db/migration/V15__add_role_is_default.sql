-- Roles can be marked as default: new users of the organisation inherit them
-- automatically on register. Off by default; can be turned on per role.
ALTER TABLE organisation_roles ADD COLUMN is_default BOOLEAN NOT NULL DEFAULT FALSE;
