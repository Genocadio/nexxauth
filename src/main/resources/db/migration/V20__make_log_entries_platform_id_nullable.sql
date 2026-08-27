-- Allow platform_id to be nullable for platform-level auth events
-- that occur outside a platform-scoped URL (e.g. /auth/register).

ALTER TABLE log_entries ALTER COLUMN platform_id DROP NOT NULL;
