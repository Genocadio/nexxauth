package com.nexxserve.nauth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Create an organisation under a platform (super user action). The slug is
 * derived from the name when not provided and made unique within the platform.
 */
public record CreateOrganisationRequest(

        @NotBlank(message = "Organisation name is required")
        @Size(max = 200, message = "Organisation name must be at most 200 characters")
        String name,

        @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$",
                message = "Slug may only contain lowercase letters, digits and single hyphens")
        @Size(max = 100, message = "Slug must be at most 100 characters")
        String slug,

        @Size(max = 1000, message = "Description must be at most 1000 characters")
        String description
) {
}
