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
        /** Primary email address (for backwards compatibility and quick access). */
        String email,
        /** Primary phone number (for backwards compatibility and quick access). */
        String phone,
        boolean enabled,
        boolean temporaryPassword,
        /** True if primary email is verified. */
        boolean emailVerified,
        /** True if primary phone is verified. */
        boolean phoneVerified,
        /** Complete list of email addresses associated with this user. */
        List<OrganisationUserEmailResponse> emails,
        /** Complete list of phone numbers associated with this user. */
        List<OrganisationUserPhoneResponse> phones,
        /** True while the user's next password login must verify their email
         * before completing (admin-set). Cleared automatically once verified. */
        boolean requireEmailVerificationAtNextLogin,
        /** True while the user's next password login must verify their phone
         * before completing (admin-set). Cleared automatically once verified. */
        boolean requirePhoneVerificationAtNextLogin,
        /** The user's enabled auth methods (PASSWORD and OTP exist today; the
         * list is the extension point for future modes such as SSO). Empty when
         * the user has no auth configured and cannot log in. */
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
