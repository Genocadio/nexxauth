package com.nexxserve.nauth.dto.request;

import com.nexxserve.nauth.validation.ValidPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Signup payload. Creates a new {@code Platform} and its first user
 * (a {@code SUPER_USER}).
 */
public record RegisterRequest(

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

        @NotBlank(message = "Platform name is required")
        @Size(max = 120, message = "Platform name must be at most 120 characters")
        String platformName,

        @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$",
                message = "Slug may only contain lowercase letters, digits and single hyphens")
        @Size(max = 100, message = "Slug must be at most 100 characters")
        String platformSlug
) {
}
