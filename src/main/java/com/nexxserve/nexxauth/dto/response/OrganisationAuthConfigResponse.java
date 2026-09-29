package com.nexxserve.nexxauth.dto.response;

import com.nexxserve.nexxauth.entity.AuthType;
import com.nexxserve.nexxauth.entity.VerificationDelivery;

/**
 * The organisation's authentication settings: the auth type users get by
 * default, the password rules (length, expiration, history) that new
 * passwords must satisfy, and the per-org verification features (email
 * verification, phone verification, password reset, OTP login, 2FA) powered
 * by nexxbotify.
 */
public record OrganisationAuthConfigResponse(
        AuthType authType,
        boolean passwordEnabled,
        int passwordMinLength,
        int passwordMaxLength,
        int passwordExpirationDays,
        int passwordHistoryCount,
        boolean emailVerificationEnabled,
        boolean phoneVerificationEnabled,
        boolean passwordResetEnabled,
        boolean otpLoginEnabled,
        boolean twoFactorEnabled,
        VerificationDelivery verificationMode,
        boolean requireEmailVerificationOnRegister,
        boolean requirePhoneVerificationOnRegister,
        /**
         * False when the notification service (nexxbotify) is not configured
         * (NEXXNOTIFY_URL unset): the verification features in this response
         * are then locked — email/phone verification, password reset, OTP
         * login and 2FA challenges are rejected with a 400 instead of
         * delivering codes/links.
         */
        boolean verificationServiceAvailable
) {
}
