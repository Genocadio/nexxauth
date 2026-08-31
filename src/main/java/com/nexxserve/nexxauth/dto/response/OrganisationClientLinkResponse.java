package com.nexxserve.nexxauth.dto.response;

import java.time.Instant;

/**
 * A registered origin (link) for a client, with its CORS and source-restriction settings.
 */
public record OrganisationClientLinkResponse(
        Long id,
        String origin,
        boolean allowCors,
        boolean limitSource,
        Instant createdAt
) {
}
