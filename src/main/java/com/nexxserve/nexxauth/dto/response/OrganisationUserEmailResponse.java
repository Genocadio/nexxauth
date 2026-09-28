package com.nexxserve.nexxauth.dto.response;

import java.time.Instant;

public record OrganisationUserEmailResponse(
        Long id,
        String email,
        boolean isPrimary,
        boolean verified,
        Instant verifiedAt,
        Instant createdAt
) {
}
