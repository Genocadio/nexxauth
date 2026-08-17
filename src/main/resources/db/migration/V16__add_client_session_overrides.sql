-- Per-client session setting overrides. When set, these values take precedence
-- over the organisation-level defaults in organisation_session_settings. Null
-- means using the org default; every existing client starts with nulls.

ALTER TABLE organisation_clients ADD COLUMN access_token_ttl_seconds BIGINT;
ALTER TABLE organisation_clients ADD COLUMN refresh_token_ttl_seconds BIGINT;
ALTER TABLE organisation_clients ADD COLUMN max_sessions_per_user INTEGER;

-- Remember which client issued a refresh token so the correct per-client
-- session settings are applied during rotation and session-limit enforcement.
ALTER TABLE organisation_refresh_tokens ADD COLUMN client_key VARCHAR(64);
