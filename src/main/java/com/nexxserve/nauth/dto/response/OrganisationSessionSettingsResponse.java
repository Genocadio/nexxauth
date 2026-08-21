package com.nexxserve.nauth.dto.response;

/**
 * Organisation-level session settings: token lifetimes and the concurrent
 * session limit. Defaults match the platform's {@code app.jwt.*} values.
 */
public record OrganisationSessionSettingsResponse(
        long accessTokenTtlSeconds,
        long refreshTokenTtlSeconds,
        int maxSessionsPerUser
) {
}
