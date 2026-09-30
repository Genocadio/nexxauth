package com.nexxserve.nexxauth.dto.response;

import com.nexxserve.nexxauth.entity.AuthType;
import com.nexxserve.nexxauth.entity.UserLoginMethod;

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
        /** Which credentials this user may sign in with. PASSWORD/OTP/
         * PASSWORD_OR_OTP; this is the administrator's per-user choice and is
         * independent of {@link #authTypes}. */
        UserLoginMethod loginMethod,
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
        /** The credentials this user can actually use right now, derived from
         * {@link #loginMethod} and what is stored: PASSWORD only when a hash
         * exists, OTP only when the user has a reachable address. Empty means
         * the user is locked out (for example no password and no address yet). */
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
