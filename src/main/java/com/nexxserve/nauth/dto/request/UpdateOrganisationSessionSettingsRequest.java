package com.nexxserve.nauth.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Partial update of an organisation's session settings: only provided fields
 * are applied. Cross-field rules (refresh must outlive access) are validated
 * in the service.
 */
public record UpdateOrganisationSessionSettingsRequest(

        /** Access-token lifetime in seconds. */
        @Min(value = 60, message = "Access token TTL must be at least 60 seconds")
        @Max(value = 86400, message = "Access token TTL cannot exceed 24 hours")
        Long accessTokenTtlSeconds,

        /** Refresh-token lifetime in seconds. */
        @Min(value = 300, message = "Refresh token TTL must be at least 300 seconds")
        @Max(value = 31536000, message = "Refresh token TTL cannot exceed one year")
        Long refreshTokenTtlSeconds,

        /** Concurrent sessions per user. */
        @Min(value = 1, message = "At least one session per user is required")
        @Max(value = 100, message = "At most 100 sessions per user are allowed")
        Integer maxSessionsPerUser
) {
}
