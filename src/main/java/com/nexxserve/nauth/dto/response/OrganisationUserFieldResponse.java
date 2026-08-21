package com.nexxserve.nauth.dto.response;

import com.nexxserve.nauth.entity.UserFieldType;

import java.time.Instant;

/**
 * An organisation-defined user field. {@code loginEnabled} is part of the
 * field configuration and is intentionally NOT echoed on the per-user
 * {@code metadata} objects.
 */
public record OrganisationUserFieldResponse(
        Long id,
        String key,
        UserFieldType fieldType,
        boolean loginEnabled,
        boolean required,
        Instant createdAt
) {
}
