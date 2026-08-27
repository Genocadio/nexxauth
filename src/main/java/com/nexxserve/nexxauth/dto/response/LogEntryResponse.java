package com.nexxserve.nexxauth.dto.response;

import java.time.Instant;

/**
 * API response for a log entry. Sent both as a paginated REST response and
 * as an SSE event payload.
 */
public record LogEntryResponse(
        Long id,
        Long organisationId,
        String organisationSlug,
        String level,
        String category,
        String eventType,
        String message,
        String actor,
        String ip,
        String requestId,
        String detail,
        String clientKey,
        String domain,
        Instant createdAt
) {}
