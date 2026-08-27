package com.nexxserve.nexxauth.dto.response;

import java.time.Instant;

/**
 * One event in a session's refresh timeline: the creation and eventual
 * revocation/expiry of a single refresh token within a session.
 */
public record SessionTimelineEvent(
        Instant createdAt,
        Instant expiresAt,
        Instant revokedAt,
        Instant evictedAt,
        boolean active,
        String clientKey
) {
}
