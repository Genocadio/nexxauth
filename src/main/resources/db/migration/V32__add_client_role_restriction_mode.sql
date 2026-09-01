-- Add role restriction mode: ALLOWLIST (only listed roles may login,
-- the current behaviour) or BLOCKLIST (listed roles are prohibited).
-- Default NONE preserves backward compatibility (no restriction when empty).

ALTER TABLE organisation_clients
    ADD COLUMN role_restriction_mode VARCHAR(20) NOT NULL DEFAULT 'NONE';
