package com.nexxserve.nexxauth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * Org-level signup: creates an org user (no roles by default) and returns
 * organisation access tokens. The identifier is the username, or the email
 * when the organisation uses email as username.
 */
public record OrgRegisterRequest(

        @NotNull(message = "Organisation id is required")
        Long organisationId,

        @NotBlank(message = "Identifier is required")
        @Size(max = 100, message = "Identifier must be at most 100 characters")
        String identifier,

        @NotBlank(message = "Password is required")
        @Size(max = 72, message = "Password must be at most 72 characters")
        String password,

        @NotBlank(message = "First name is required")
        @Size(max = 100, message = "First name must be at most 100 characters")
        String firstName,

        @NotBlank(message = "Last name is required")
        @Size(max = 100, message = "Last name must be at most 100 characters")
        String lastName,

        /** Optional values for the organisation's configured user fields.
         * Keys must be defined fields; values are validated against each
         * field's type. Blank or null values are ignored. */
        @Size(max = 100, message = "Metadata must contain at most 100 entries")
        Map<String, String> metadata
) {
}
