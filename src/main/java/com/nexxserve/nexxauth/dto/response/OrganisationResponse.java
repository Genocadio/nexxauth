package com.nexxserve.nexxauth.dto.response;

import java.time.Instant;

public record OrganisationResponse(
        Long id,
        String name,
        String slug,
        String description,
        boolean useEmailAsUsername,
        Instant createdAt
) {
}
