package com.nexxserve.nexxauth.dto.response;

import java.time.Instant;

public record OrganisationResponse(
        Long id,
        String name,
        String slug,
        String description,
        boolean emailRequired,
        boolean usernameRequired,
        boolean phoneRequired,
        boolean emailCanLogin,
        boolean usernameCanLogin,
        boolean phoneCanLogin,
        /** Onboarding wizard progress: 1..7 = current step, 8 = complete, null = not started. */
        Integer onboardingStep,
        /** Backwards-compatible: true when email is the primary login identifier. */
        boolean useEmailAsUsername,
        Instant createdAt
) {
}
