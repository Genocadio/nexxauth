package com.nexxserve.nexxauth.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Partial update of an organisation: only provided fields are applied. The
 * owning platform's slug is immutable and never part of this request.
 */
public record UpdateOrganisationRequest(

        @Size(min = 1, max = 200, message = "Organisation name must be between 1 and 200 characters")
        String name,

        @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$",
                message = "Slug may only contain lowercase letters, digits and single hyphens")
        @Size(min = 1, max = 100, message = "Slug must be between 1 and 100 characters")
        String slug,

        @Size(max = 1000, message = "Description must be at most 1000 characters")
        String description,

        /** When true, organisation users identify by email (email becomes required). */
        Boolean useEmailAsUsername
) {
}
