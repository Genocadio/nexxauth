package com.nexxserve.nexxauth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Org-level login: the organisation the user belongs to, the login identifier
 * (username, email when the organisation uses email as username, or the value
 * of a login-enabled user field) and the password. Returns organisation access
 * tokens signed by the org's own key. The identifier may be up to 255 chars so
 * any user-field value (also capped at 255) can serve as a login identifier.
 */
public record OrgLoginRequest(

        @NotNull(message = "Organisation id is required")
        Long organisationId,

        @NotBlank(message = "Identifier is required")
        @Size(max = 255, message = "Identifier must be at most 255 characters")
        String identifier,

        @NotBlank(message = "Password is required")
        String password
) {
}
