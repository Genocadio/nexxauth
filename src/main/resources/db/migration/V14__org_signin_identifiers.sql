-- Organisation sign-in identifiers become configurable: email, username and
-- phone are all available by default, each with an independent "required" and
-- "can login" flag (at least one must be able to login). Replaces the single
-- use_email_as_username boolean, which is backfilled and kept for compatibility.
ALTER TABLE organisation_users ADD COLUMN phone VARCHAR(30);

-- Phone is unique per organisation when set (NULLs are allowed to repeat).
ALTER TABLE organisation_users
    ADD CONSTRAINT uk_organisation_users_phone UNIQUE (organisation_id, phone);

ALTER TABLE organisations ADD COLUMN email_required BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE organisations ADD COLUMN username_required BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE organisations ADD COLUMN phone_required BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE organisations ADD COLUMN email_can_login BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE organisations ADD COLUMN username_can_login BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE organisations ADD COLUMN phone_can_login BOOLEAN NOT NULL DEFAULT FALSE;
-- Onboarding wizard progress: 1..7 = step the user is on, 8 = done, NULL = not started.
ALTER TABLE organisations ADD COLUMN onboarding_step INTEGER;

-- Backfill: an org that used email as username requires email and logs in by
-- email; the rest require a username and log in by username. Phone is never
-- enabled by the old setting.
UPDATE organisations SET
    email_required = use_email_as_username,
    email_can_login = use_email_as_username,
    username_required = NOT use_email_as_username,
    username_can_login = NOT use_email_as_username
WHERE use_email_as_username IS NOT NULL;

-- Password authentication can be disabled per organisation (reserved for the
-- future: other methods are "coming soon"). Default: enabled.
ALTER TABLE organisation_auth_configs ADD COLUMN password_enabled BOOLEAN NOT NULL DEFAULT TRUE;
