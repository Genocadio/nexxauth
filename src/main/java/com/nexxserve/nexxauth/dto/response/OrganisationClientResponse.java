package com.nexxserve.nexxauth.dto.response;

import com.nexxserve.nexxauth.entity.ClientType;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * A client's configuration. {@code token} is only ever present on create and
 * rotate responses (the static token is shown exactly once); all other
 * responses carry {@code null} so it never leaks.
 */
public record OrganisationClientResponse(
        Long id,
        String name,
        ClientType type,
        boolean requireAuthentication,
        List<String> allowedOrigins,
        boolean enabled,
        Map<String, String> settings,
        Instant createdAt,
        String token
) {
}
