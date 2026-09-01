-- Composite indexes for the most common query patterns.
-- These cover the dashboard log query (organisation + level + time range)
-- and the session-enforcement query (user + active status).

-- Log dashboard: filtering by organisation + level + time range
CREATE INDEX idx_log_entries_org_level_created
    ON log_entries (organisation_id, level, created_at DESC);

-- Refresh token session counting: active tokens per user
-- (not revoked, not evicted, not expired, ordered by expiry)
CREATE INDEX idx_org_refresh_tokens_user_active
    ON organisation_refresh_tokens (organisation_user_id, expires_at, revoked_at, evicted_at);

-- Session dedup lookup: user + IP + agent for same-session reuse
CREATE INDEX idx_org_refresh_tokens_session_dedup
    ON organisation_refresh_tokens (organisation_user_id, ip_address, user_agent, created_at DESC);
