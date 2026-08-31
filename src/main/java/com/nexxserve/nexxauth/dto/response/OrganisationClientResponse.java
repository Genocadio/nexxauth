package com.nexxserve.nexxauth.dto.response;

import com.nexxserve.nexxauth.entity.ClientType;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A client's configuration. {@code clientKey} is the opaque identifier apps
 * send as {@code X-Client-Id}. {@code token} is only ever present on create and
 * rotate responses (the static token is shown exactly once); all other
 * responses carry {@code null} so it never leaks.
 */
public record OrganisationClientResponse(
        String clientKey,
        String name,
        ClientType type,
        boolean requireAuthentication,
        List<String> allowedOrigins,
        boolean enabled,
        Map<String, String> settings,
        Instant createdAt,
        String token,
        // --- session overrides (null = using org defaults) ---
        Long accessTokenTtlSeconds,
        Long refreshTokenTtlSeconds,
        Integer maxSessionsPerUser,
        // --- per-client login/register restrictions ---
        boolean allowRegister,
        boolean allowLogin,
        /** Parsed set of allowed role names; empty means no restriction. */
        Set<String> allowedRoles,
        // --- link management ---
        /** Per-link CORS and source-restriction settings. */
        List<OrganisationClientLinkResponse> links
) {
}
