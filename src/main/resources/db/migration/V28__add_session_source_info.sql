-- Source domain/hostname and client type captured at token issue time.
-- hostname: ends with ':origin' when derived from the Origin header (web client),
-- otherwise reverse-DNS/forward entry of the client IP.
ALTER TABLE organisation_refresh_tokens
    ADD COLUMN hostname VARCHAR(255);
