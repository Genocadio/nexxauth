package com.nexxserve.nexxauth.dto.request;

import com.nexxserve.nexxauth.entity.Permission;
import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * Partial update of an organisation role: only provided fields are applied.
 * Passing {@code permissions} replaces the whole set (including with empty).
 */
public record UpdateOrganisationRoleRequest(

        @Size(min = 1, max = 100, message = "Role name must be between 1 and 100 characters")
        String name,

        Set<Permission> permissions
) {
}
