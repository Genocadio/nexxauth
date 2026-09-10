package com.nexxserve.nexxauth.dto.response;

import com.nexxserve.nexxauth.entity.VerificationChannel;
import com.nexxserve.nexxauth.entity.VerificationDelivery;
import com.nexxserve.nexxauth.entity.VerificationPurpose;

/**
 * Acknowledgement of a verification delivery request: what was sent, to where,
 * and for how long the value stays valid before it must be re-requested.
 */
public record VerificationRequestResponse(
        VerificationPurpose purpose,
        VerificationChannel channel,
        VerificationDelivery delivery,
        String identifier,
        long expiresInSeconds
) {
}