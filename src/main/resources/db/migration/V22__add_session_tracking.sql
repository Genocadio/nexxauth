-- Add session tracking fields to organisation refresh tokens.
-- Each token now carries the IP and user-agent of the client that created it,
-- plus a session UUID that groups tokens belonging to the same logical session.
-- On token rotation the new token inherits the parent's session id so the
-- session remains trackable across rotations.

ALTER TABLE organisation_refresh_tokens
    ADD COLUMN ip_address VARCHAR(45);

ALTER TABLE organisation_refresh_tokens
    ADD COLUMN user_agent VARCHAR(2000);

ALTER TABLE organisation_refresh_tokens
    ADD COLUMN session_id VARCHAR(36);

-- Index for session lookups: find all tokens in a session, or find sessions
-- for a user/org.
CREATE INDEX idx_org_refresh_tokens_session_id
    ON organisation_refresh_tokens (session_id);

CREATE INDEX idx_org_refresh_tokens_org_user
    ON organisation_refresh_tokens (organisation_user_id, revoked_at, evicted_at, expires_at);
