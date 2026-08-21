package com.nexxserve.nexxauth.dto.request;

import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * Self-service partial update of an organisation user's own profile: only the
 * provided fields are applied. Used to complete the UPDATE_PROFILE action
 * (e.g. filling values for required organisation user fields).
 */
public record UpdateOwnProfileRequest(

        @Size(min = 1, max = 100, message = "First name must be between 1 and 100 characters")
        String firstName,

        /** Optional; pass "" to clear, omit to keep. */
        @Size(max = 100, message = "Last name must be at most 100 characters")
        String lastName,

        /** Optional user-field values: partial, only the keys present are
         * touched. A null or blank value removes the field from the user;
         * values are validated against each field's type. */
        @Size(max = 100, message = "Metadata must contain at most 100 entries")
        Map<String, String> metadata
) {
}
