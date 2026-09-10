-- 2FA, verification delivery mode, register-time verification requirements,
-- and per-user "verify at next login" enforcement.

-- Verification policy on the org auth config:
-- * two_factor_enabled: password logins require a second factor (server-sent OTP).
-- * verification_mode: default delivery (OTP or LINK) when a request omits it.
-- * require_*_verification_on_register: new registrations are gated until the
--   respective identifier is verified.
ALTER TABLE organisation_auth_configs ADD COLUMN two_factor_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE organisation_auth_configs ADD COLUMN verification_mode VARCHAR(10) NOT NULL DEFAULT 'OTP';
ALTER TABLE organisation_auth_configs ADD COLUMN require_email_verification_on_register BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE organisation_auth_configs ADD COLUMN require_phone_verification_on_register BOOLEAN NOT NULL DEFAULT FALSE;

-- Per-user flags: force a "verify at next login" challenge for the identifier.
ALTER TABLE organisation_users ADD COLUMN require_email_verification_at_next_login BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE organisation_users ADD COLUMN require_phone_verification_at_next_login BOOLEAN NOT NULL DEFAULT FALSE;