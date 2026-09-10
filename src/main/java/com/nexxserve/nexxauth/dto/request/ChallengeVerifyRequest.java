package com.nexxserve.nexxauth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Submits the code for a pending login challenge (2FA or verify-at-next-login)
 * that was returned by a password login or a previous challenge verification.
 * A successful challenge either returns another challenge (when more
 * obligations remain) or the full {@code OrgAuthResponse} session.
 */
public record ChallengeVerifyRequest(

        /** The challenge token from the login / previous challenge response. */
        @NotBlank(message = "Challenge token is required")
        String challengeToken,

        /** The one-time code the server sent to the user's identifier. */
        @NotBlank(message = "Code is required")
        @Size(max = 10, message = "Code must be at most 10 characters")
        String code
) {
}