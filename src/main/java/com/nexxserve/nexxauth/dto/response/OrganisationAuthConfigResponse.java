package com.nexxserve.nexxauth.dto.response;

import com.nexxserve.nexxauth.entity.AuthType;

/**
 * The organisation's authentication settings: the auth type users get by
 * default and the password rules (length, expiration, history) that new
 * passwords must satisfy.
 */
public record OrganisationAuthConfigResponse(
        AuthType authType,
        int passwordMinLength,
        int passwordMaxLength,
        int passwordExpirationDays,
        int passwordHistoryCount
) {
}
