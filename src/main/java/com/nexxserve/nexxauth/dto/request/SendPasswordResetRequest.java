package com.nexxserve.nexxauth.dto.request;

import com.nexxserve.nexxauth.entity.VerificationDelivery;

/**
 * Admin-triggered password reset: sends the user a code or link so they choose
 * their own password, rather than the admin setting one and reading it out over
 * the phone. Identify the delivery address with {@code emailId} or
 * {@code phoneId}; with neither, the user's primary address is used.
 */
public record SendPasswordResetRequest(
        Long emailId,

        Long phoneId,

        /** OTP or magic link. Omit to follow the organisation's verification mode. */
        VerificationDelivery delivery
) {
}
