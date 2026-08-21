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
        /** The user's enabled auth methods (only PASSWORD exists today; the
         * list is the extension point for future modes such as OTP or SSO).
         * Empty when the user has no auth configured and cannot log in. */
        List<AuthType> authTypes,
        /** The names of the roles the user holds — never ids, and never
         * permissions (permissions are an internal concept, resolved
         * server-side on every request). */
        List<String> roles,
        Instant createdAt,
        /** Values of the organisation's configured user fields, keyed by field
         * key. Whether a field is used for login is config-level and is not
         * reflected here. */
        Map<String, String> metadata
) {
}
