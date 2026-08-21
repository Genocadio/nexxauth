package com.nexxserve.nexxauth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;
import java.util.Set;

/**
 * Create an organisation user. Username and email are optional identifiers,
 * unique per organisation; when the organisation has {@code useEmailAsUsername}
 * enabled, email is required. A user created without a {@code password} has no
 * auth configured and cannot log in (per the org auth config rules).
 */
public record CreateOrganisationUserRequest(

        @NotBlank(message = "First name is required")
        @Size(max = 100, message = "First name must be at most 100 characters")
        String firstName,

        /** Optional; null or blank is stored as null. */
        @Size(max = 100, message = "Last name must be at most 100 characters")
        String lastName,

        @Size(max = 100, message = "Username must be at most 100 characters")
        String username,

        @Email(message = "Email must be valid")
        @Size(max = 255, message = "Email must be at most 255 characters")
        String email,

        @Size(max = 30, message = "Phone must be at most 30 characters")
        String phone,

        Set<Long> roleIds,

        /** Optional initial password. When set, the user gets the org's default
         * auth type (PASSWORD) and can log in; when omitted, the user has no
         * auth configured and cannot log in until a password is set. */
        @Size(max = 72, message = "Password must be at most 72 characters")
        String password,

        /** When true, {@code password} is temporary: at the user's first login
         * the CHANGE_PASSWORD action is returned, no refresh token is issued,
         * and the access token is fixed at 5 minutes until the user sets a new
         * password. Ignored when no password is set. */
        Boolean temporaryPassword,

        /** Optional values for the organisation's configured user fields. Keys
         * must be defined fields; values are validated against each field's
         * type. Blank or null values are ignored (the field stays unset). */
        @Size(max = 100, message = "Metadata must contain at most 100 entries")
        Map<String, String> metadata
) {
}
