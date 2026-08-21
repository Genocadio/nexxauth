package com.nexxserve.nauth.dto.request;

import com.nexxserve.nauth.entity.Permission;
import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * Partial update of an organisation role: only provided fields are applied.
 * Passing {@code permissions} replaces the whole set (including with empty).
 */
public record UpdateOrganisationRoleRequest(

        @Size(min = 1, max = 100, message = "Role name must be between 1 and 100 characters")
        String name,

        Set<Permission> permissions,

        /** When true, new users of the organisation inherit this role
         * automatically on register; false turns it off. */
        Boolean isDefault
) {
}
