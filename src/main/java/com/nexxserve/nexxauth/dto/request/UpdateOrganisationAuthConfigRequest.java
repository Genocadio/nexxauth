package com.nexxserve.nexxauth.dto.request;

import com.nexxserve.nexxauth.entity.AuthType;
import com.nexxserve.nexxauth.entity.VerificationDelivery;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Partial update of an organisation's auth config: only provided fields are
 * applied. Cross-field rules (min &lt;= max, byte bounds) are validated in the
 * service.
 */
public record UpdateOrganisationAuthConfigRequest(

        AuthType authType,

        /** When false, password authentication is disabled for the org; users
         * cannot sign in until another method is enabled. */
        Boolean passwordEnabled,

        @Min(value = 1, message = "Password minimum length must be at least 1")
        @Max(value = 72, message = "Password minimum length must be at most 72")
        Integer passwordMinLength,

        @Min(value = 1, message = "Password maximum length must be at least 1")
        @Max(value = 72, message = "Password maximum length must be at most 72")
        Integer passwordMaxLength,

        @Min(value = 0, message = "Password expiration days cannot be negative")
        @Max(value = 3650, message = "Password expiration cannot exceed 3650 days")
        Integer passwordExpirationDays,

        @Min(value = 0, message = "Password history count cannot be negative")
        @Max(value = 50, message = "Password history count must be at most 50")
        Integer passwordHistoryCount,

        /** When true, users' email addresses must be verified via an OTP or
         * magic link sent through nexxbotify. */
        Boolean emailVerificationEnabled,

        /** When true, users' phone numbers must be verified via an OTP or
         * magic link sent through nexxbotify. */
        Boolean phoneVerificationEnabled,

        /** When true, users may reset a forgotten password through a code or
         * link sent through nexxbotify. */
        Boolean passwordResetEnabled,

        /** When true, users may sign in with a one-time code sent to their
         * identifier (OTP login) instead of a password. */
        Boolean otpLoginEnabled,

        /** When true, every password login requires a second factor (OTP):
         * the server sends a code and the login completes on the challenge
         * endpoint. */
        Boolean twoFactorEnabled,

        /** Default delivery (OTP or LINK) for verification requests that do
         * not specify one. */
        VerificationDelivery verificationMode,

        /** When true, new registrations can only reach full access after
         * verifying their email (gating VERIFY_EMAIL action until verified). */
        Boolean requireEmailVerificationOnRegister,

        /** When true, new registrations can only reach full access after
         * verifying their phone (gating VERIFY_PHONE action until verified). */
        Boolean requirePhoneVerificationOnRegister
) {
}
