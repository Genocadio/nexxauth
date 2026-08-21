package com.nexxserve.nauth.dto.response;

import com.nexxserve.nauth.entity.AuthType;

/**
 * The organisation's authentication settings: the auth type users get by
 * default and the password rules (length, expiration, history) that new
 * passwords must satisfy.
 */
public record OrganisationAuthConfigResponse(
        AuthType authType,
        boolean passwordEnabled,
        int passwordMinLength,
        int passwordMaxLength,
        int passwordExpirationDays,
        int passwordHistoryCount
) {
}
