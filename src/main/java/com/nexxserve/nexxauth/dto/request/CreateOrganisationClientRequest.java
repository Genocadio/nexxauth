package com.nexxserve.nexxauth.dto.request;

import com.nexxserve.nexxauth.entity.ClientType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Create a client. {@code type} is immutable after creation. The type drives
 * {@code requireAuthentication}: WEB is always false, SERVER always true, and
 * the mobile app types take the supplied value (default false). Only
 * auth-required clients get a static token, generated server-side and shown in
 * the create response exactly once.
 */
public record CreateOrganisationClientRequest(

        @NotBlank(message = "Name is required")
        @Size(max = 100, message = "Name must be at most 100 characters")
        String name,

        @NotNull(message = "Type is required")
        ClientType type,

        Boolean requireAuthentication,

        @Size(max = 20, message = "At most 20 allowed origins")
        List<@NotBlank(message = "Allowed origins must not be blank") String> allowedOrigins,

        Boolean enabled,

        @Size(max = 50, message = "Settings must contain at most 50 entries")
        Map<String, String> settings,

        // --- session overrides (null = use org defaults) ---

        Long accessTokenTtlSeconds,

        Long refreshTokenTtlSeconds,

        Integer maxSessionsPerUser,

        // --- per-client login/register restrictions ---

        Boolean allowRegister,

        Boolean allowLogin,

        @Size(max = 50, message = "At most 50 allowed roles")
        Set<@NotBlank(message = "Role name must not be blank") String> allowedRoles
) {
}
