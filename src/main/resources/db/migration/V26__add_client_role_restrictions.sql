-- Per-client login/register restrictions.
-- allow_register: when false, registration via this client is blocked (403).
-- allow_login:    when false, login via this client is blocked (403).
-- allowed_roles:  comma-separated role names; when non-null only users holding
--                 at least one of these roles may login/register via this client.
--                 Null means no role restriction (all roles allowed).

ALTER TABLE organisation_clients ADD COLUMN allow_register BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE organisation_clients ADD COLUMN allow_login BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE organisation_clients ADD COLUMN allowed_roles VARCHAR(500);
