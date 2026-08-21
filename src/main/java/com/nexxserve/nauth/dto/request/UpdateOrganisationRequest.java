package com.nexxserve.nauth.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Partial update of an organisation: only provided fields are applied. The
 * owning platform's slug is immutable and never part of this request.
 * <p>
 * Sign-in identifiers: each of email/username/phone has an independent
 * {@code required} and {@code canLogin} flag; at least one identifier must be
 * able to login (validated in the service). The legacy {@code useEmailAsUsername}
 * switch is kept for compatibility — setting it maps to the new flags.
 */
public record UpdateOrganisationRequest(

        @Size(min = 1, max = 200, message = "Organisation name must be between 1 and 200 characters")
        String name,

        @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$",
                message = "Slug may only contain lowercase letters, digits and single hyphens")
        @Size(min = 1, max = 100, message = "Slug must be between 1 and 100 characters")
        String slug,

        @Size(max = 1000, message = "Description must be at most 1000 characters")
        String description,

        /** Legacy switch: true = email required + login identifier, username not. */
        Boolean useEmailAsUsername,

        Boolean emailRequired,

        Boolean usernameRequired,

        Boolean phoneRequired,

        Boolean emailCanLogin,

        Boolean usernameCanLogin,

        Boolean phoneCanLogin,

        /** Onboarding wizard progress: 1..7 = step reached, 8 = complete. */
        @Min(value = 1, message = "Onboarding step must be between 1 and 8")
        @Max(value = 8, message = "Onboarding step must be between 1 and 8")
        Integer onboardingStep
) {
}
