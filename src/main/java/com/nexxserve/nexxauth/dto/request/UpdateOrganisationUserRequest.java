package com.nexxserve.nexxauth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

import java.util.Map;
import java.util.Set;

/**
 * Partial update of an organisation user: only provided fields are applied.
 * {@code username}/{@code email} may be passed as empty strings to clear them;
 * {@code roleIds} replaces the whole role set (empty clears all roles);
 * {@code password} resets the password subject to the org's password rules
 * (empty string clears auth and disables login).
 */
public record UpdateOrganisationUserRequest(

        @Size(min = 1, max = 100, message = "First name must be between 1 and 100 characters")
        String firstName,

        @Size(min = 1, max = 100, message = "Last name must be between 1 and 100 characters")
        String lastName,

        @Size(max = 100, message = "Username must be at most 100 characters")
        String username,

        @Email(message = "Email must be valid")
        @Size(max = 255, message = "Email must be at most 255 characters")
        String email,

        Boolean enabled,

        Set<Long> roleIds,

        /** Optional password reset: validated against the org's password rules
         * (length + reuse history). A user created without a password can be
         * given one here; set {@code ""} to clear the auth and disable login. */
        @Size(max = 72, message = "Password must be at most 72 characters")
        String password,

        /** When true, forces the user to change their password at next login
         * (CHANGE_PASSWORD action, gated session) and revokes existing
         * sessions; when false, clears any pending forced change. */
        Boolean temporaryPassword,

        /** Optional user-field values: partial, only the keys present are
         * touched. A null or blank value removes the field from the user;
         * values are validated against each field's type and login-enabled
         * fields must stay unique per org. */
        @Size(max = 100, message = "Metadata must contain at most 100 entries")
        Map<String, String> metadata
) {
}
