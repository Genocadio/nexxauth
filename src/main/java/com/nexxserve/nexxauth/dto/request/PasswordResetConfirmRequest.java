package com.nexxserve.nexxauth.dto.request;

import com.nexxserve.nexxauth.entity.VerificationChannel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Final step of a password reset: the code (numeric OTP) or magic-link token
 * received on {@code identifier} plus the new password. A valid value lets an
 * unauthenticated caller set a new password for the account that owns the
 * identifier — the identical flow when the org delivers resets by link or by
 * OTP.
 * <p>
 * <b>External clients must never send {@code organisationId}.</b> The
 * organisation is resolved automatically from the {@code X-Client-Id} header.
 * The {@code organisationId} field is an internal detail used only by the
 * platform console portal flow (when no client header is present).
 */
public record PasswordResetConfirmRequest(

        @NotBlank(message = "Identifier is required")
        @Size(max = 255, message = "Identifier must be at most 255 characters")
        String identifier,

        @NotNull(message = "Channel is required")
        VerificationChannel channel,

        /** The numeric OTP or the opaque magic-link token. */
        @NotBlank(message = "Token is required")
        @Size(max = 200, message = "Token must be at most 200 characters")
        String token,

        @NotBlank(message = "New password is required")
        @Size(max = 72, message = "Password must be at most 72 characters")
        String newPassword,

        /** Organisation ID — <b>internal only</b>. Used by the platform console
         *  portal flow when no {@code X-Client-Id} header is present. External
         *  clients must never send this field; the organisation is resolved from
         *  the client header. The client header takes precedence when both are
         *  supplied. */
        Long organisationId
) {
}