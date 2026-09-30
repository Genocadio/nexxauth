package com.nexxserve.nexxauth.dto.request;

import com.nexxserve.nexxauth.entity.VerificationChannel;
import com.nexxserve.nexxauth.entity.VerificationDelivery;
import jakarta.validation.constraints.NotNull;

/**
 * Admin-triggered verification send for one of a user's own addresses.
 *
 * <p>Identify the target with {@code emailId} or {@code phoneId}; when both are
 * omitted the user's primary address for {@code channel} is used. Passing an id
 * that does not belong to the user is rejected rather than silently redirected
 * to their primary address, so a caller cannot accidentally send to the wrong
 * mailbox.
 */
public record SendUserVerificationRequest(
        @NotNull(message = "Channel is required")
        VerificationChannel channel,

        Long emailId,

        Long phoneId,

        /** OTP or magic link. Omit to follow the organisation's verification mode. */
        VerificationDelivery delivery
) {
}
