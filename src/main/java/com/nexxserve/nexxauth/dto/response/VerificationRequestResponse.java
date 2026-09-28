package com.nexxserve.nexxauth.dto.response;

import com.nexxserve.nexxauth.entity.VerificationChannel;
import com.nexxserve.nexxauth.entity.VerificationDelivery;
import com.nexxserve.nexxauth.entity.VerificationPurpose;

/**
 * Acknowledgement of a verification delivery request: what was sent, to where,
 * and for how long the value stays valid before it must be re-requested.
 * Returns an {@code accessToken} (short-lived action token matching {@code expiresInSeconds})
 * to bind subsequent verification / password-reset confirmation to this session.
 */
public record VerificationRequestResponse(
        VerificationPurpose purpose,
        VerificationChannel channel,
        VerificationDelivery delivery,
        String identifier,
        long expiresInSeconds,
        String accessToken,
        String tokenType
) {
    public VerificationRequestResponse(VerificationPurpose purpose,
                                       VerificationChannel channel,
                                       VerificationDelivery delivery,
                                       String identifier,
                                       long expiresInSeconds,
                                       String accessToken) {
        this(purpose, channel, delivery, identifier, expiresInSeconds, accessToken, "Bearer");
    }

    public VerificationRequestResponse(VerificationPurpose purpose,
                                       VerificationChannel channel,
                                       VerificationDelivery delivery,
                                       String identifier,
                                       long expiresInSeconds) {
        this(purpose, channel, delivery, identifier, expiresInSeconds, null, null);
    }
}