package com.nexxserve.nauth.dto.request;

import jakarta.validation.constraints.Size;

/**
 * Partial self-update: only provided fields are applied.
 */
public record UpdateProfileRequest(

        @Size(min = 1, max = 100, message = "First name must be between 1 and 100 characters")
        String firstName,

        @Size(min = 1, max = 100, message = "Last name must be between 1 and 100 characters")
        String lastName,

        @Size(min = 1, max = 30, message = "Phone must be between 1 and 30 characters")
        String phone
) {
}
