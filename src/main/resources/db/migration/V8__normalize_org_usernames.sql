-- Usernames are now normalized to lowercase before storage and lookup so
-- Bob and bob are the same account (mirror of the email normalization). Bring
-- any previously stored mixed-case usernames in line. This fails fast if an
-- organisation already contains both "Bob" and "bob" as distinct users; no such
-- data exists in a fresh deployment.
UPDATE organisation_users SET username = lower(username) WHERE username IS NOT NULL;
