package com.nexxserve.nexxauth.dto.response;

import java.time.Instant;

/**
 * A resolved session: one logical session (same IP + user-agent + session UUID)
 * that may have been through several token rotations. Shown in the console's
 * session management UI.
 */
public record OrganisationSessionResponse(
        String sessionId,
        Long userId,
        String userIdentifier,
        String ipAddress,
        String userAgent,
        String clientKey,
        String clientName,
        String clientType,
        String hostname,
        Instant createdAt,
        Instant lastActivityAt,
        Instant expiresAt,
        boolean active,
        int tokenCount
) {
}
