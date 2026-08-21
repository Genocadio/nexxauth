package com.nexxserve.nauth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/**
 * Update a client. The type cannot be changed. Turning {@code requireAuthentication}
 * on for an auth-capable client generates a fresh token; turning it off clears it.
 */
public record UpdateOrganisationClientRequest(

        @Size(max = 100, message = "Name must be at most 100 characters")
        String name,

        Boolean requireAuthentication,

        @Size(max = 20, message = "At most 20 allowed origins")
        List<@NotBlank(message = "Allowed origins must not be blank") String> allowedOrigins,

        Boolean enabled,

        @Size(max = 50, message = "Settings must contain at most 50 entries")
        Map<String, String> settings,

        // --- session overrides (null = no change, use -1 to clear back to org default) ---

        Long accessTokenTtlSeconds,

        Long refreshTokenTtlSeconds,

        Integer maxSessionsPerUser
) {
}
