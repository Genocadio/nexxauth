package com.nexxserve.nexxauth.dto.response;

import com.nexxserve.nexxauth.entity.AuthType;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record OrganisationUserResponse(
        Long id,
        String firstName,
        String lastName,
        String username,
        String email,
        String phone,
        boolean enabled,
        boolean temporaryPassword,
        AuthType authType,
        /** The roles the user holds (id + name only — permissions are never
         * returned on user responses). */
        List<OrganisationUserRoleResponse> roles,
        Instant createdAt,
        /** Values of the organisation's configured user fields, keyed by field
         * key. Whether a field is used for login is config-level and is not
         * reflected here. */
        Map<String, String> metadata
) {
}
