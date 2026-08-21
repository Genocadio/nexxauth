package com.nexxserve.nexxauth.dto.request;

import com.nexxserve.nexxauth.entity.Role;
import com.nexxserve.nexxauth.validation.ValidPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Add a new member to a platform (super user action). Role defaults to
 * {@link Role#READ_ONLY} when omitted.
 */
public record AddPlatformUserRequest(

        @NotBlank(message = "First name is required")
        @Size(max = 100, message = "First name must be at most 100 characters")
        String firstName,

        @NotBlank(message = "Last name is required")
        @Size(max = 100, message = "Last name must be at most 100 characters")
        String lastName,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        @Size(max = 255, message = "Email must be at most 255 characters")
        String email,

        @NotBlank(message = "Password is required")
        @ValidPassword
        String password,

        @Size(max = 30, message = "Phone must be at most 30 characters")
        String phone,

        Role role
) {
}
