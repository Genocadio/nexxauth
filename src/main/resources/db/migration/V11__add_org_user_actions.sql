-- Organisation user actions: pending things a user must resolve before (or
-- alongside) normal access, surfaced as an `actions` array on org login.

-- A temporary password means the user MUST change it at next login (the
-- CHANGE_PASSWORD action). Set when a platform user creates the account with a
-- temporary password or explicitly triggers a forced password change; cleared
-- once the user changes the password themselves.
ALTER TABLE organisation_users ADD COLUMN temporary_password BOOLEAN NOT NULL DEFAULT FALSE;

-- A required user field means every user of the organisation must have a value
-- for it. Users missing a required value get the UPDATE_PROFILE action at login
-- (non-gating: it does not restrict tokens, only informs the client).
ALTER TABLE organisation_user_fields ADD COLUMN required BOOLEAN NOT NULL DEFAULT FALSE;
