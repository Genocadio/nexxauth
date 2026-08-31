package com.nexxserve.nexxauth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Create a link (registered origin) for a client.
 */
public record CreateOrganisationClientLinkRequest(

        @NotBlank(message = "Origin is required")
        @Size(max = 2000, message = "Origin must be at most 2000 characters")
        String origin,

        Boolean allowCors,

        Boolean limitSource
) {
}
