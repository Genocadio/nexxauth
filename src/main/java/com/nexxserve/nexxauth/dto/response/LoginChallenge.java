package com.nexxserve.nexxauth.dto.response;

import com.nexxserve.nexxauth.entity.VerificationChannel;
import com.nexxserve.nexxauth.entity.VerificationDelivery;
import com.nexxserve.nexxauth.entity.VerificationPurpose;

/**
 * A pending login challenge returned by a password login (2FA or a forced
 * "verify at next login"): the server has already sent a one-time code to the
 * user and the login only completes after it is submitted back on
 * {@code POST /{slug}/auth/challenges/verify}. The challenge token is a
 * short-lived signed value that identifies the pending login; {@code purpose}
 * and {@code channel} tell the client what is being proved.
 */
public record LoginChallenge(
        String challengeToken,
        VerificationPurpose purpose,
        VerificationChannel channel,
        VerificationDelivery delivery,
        long expiresInSeconds
) {
}