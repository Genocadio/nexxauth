package com.nexxserve.nexxauth.dto.request;

import jakarta.validation.constraints.Size;

/**
 * Update a link (registered origin) for a client. All fields are optional —
 * omitted fields are treated as "unchanged" (PATCH semantics).
 */
public record UpdateOrganisationClientLinkRequest(

        @Size(max = 2000, message = "Origin must be at most 2000 characters")
        String origin,

        Boolean allowCors,

        Boolean limitSource
) {
}
