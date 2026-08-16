package com.nexxserve.nexxauth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * Org-level signup: creates an org user (no roles by default) and returns
 * organisation access tokens. The organisation is identified by the
 * {@code X-Client-Id} header when the request comes from a registered client
 * (the client's organisation is authoritative); otherwise {@code organisationId}
 * is required (server-side/platform-user flows). Username, email and phone are
 * optional identifiers, unique per organisation; each is required or
 * login-enabled per the organisation's sign-in identifier configuration.
 */
public record OrgRegisterRequest(

        /** Required only when no {@code X-Client-Id} header is present. */
        Long organisationId,

        @Size(max = 100, message = "Username must be at most 100 characters")
        String username,

        @Email(message = "Email must be valid")
        @Size(max = 255, message = "Email must be at most 255 characters")
        String email,

        @Size(max = 30, message = "Phone must be at most 30 characters")
        String phone,

        /** Required while password auth is enabled for the organisation; when
         * password auth is disabled a user may register without one (they get
         * no auth configured until a method is enabled). */
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