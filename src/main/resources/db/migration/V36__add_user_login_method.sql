-- Per-user login method: an administrator picks, per user, whether the account
-- signs in with a password, a one-time code, or either. Before this column the
-- only lever was clearing password_hash, which left auth_type NULL and locked
-- the user out entirely — there was no way to express "no password, but OTP".
--
-- The default is PASSWORD_OR_OTP, not PASSWORD: before this column any user
-- with a password could also sign in with a one-time code, so a PASSWORD default
-- would silently revoke OTP login for every existing account. PASSWORD and OTP
-- are the opt-in restrictions.
ALTER TABLE organisation_users
    ADD COLUMN login_method VARCHAR(30) NOT NULL DEFAULT 'PASSWORD_OR_OTP';

-- Users whose password was cleared can still get in with a code, so make that
-- explicit rather than leaving it implicit.
UPDATE organisation_users
SET login_method = 'OTP'
WHERE auth_type IS NULL AND password_hash IS NULL;

-- The verification controllers resolve a user by identifier; a per-user method
-- is read on every login so it wants an index on the credential columns.
CREATE INDEX idx_organisation_users_login_method
    ON organisation_users (organisation_id, login_method);
