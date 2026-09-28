package com.nexxserve.nexxauth.dto.response;

import java.time.Instant;

public record OrganisationUserPhoneResponse(
        Long id,
        String phone,
        boolean isPrimary,
        boolean verified,
        Instant verifiedAt,
        Instant createdAt
) {
}
