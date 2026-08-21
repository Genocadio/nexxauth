package com.nexxserve.nauth.dto.request;

import com.nexxserve.nauth.entity.Permission;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * Create an organisation role: a named group of app-fixed permissions. The
 * permission set may be empty (a role with no permissions is valid).
 */
public record CreateOrganisationRoleRequest(

        @NotBlank(message = "Role name is required")
        @Size(max = 100, message = "Role name must be at most 100 characters")
        String name,

        Set<Permission> permissions,

        /** When true, new users of the organisation inherit this role
         * automatically on register. Defaults to false when omitted. */
        Boolean isDefault
) {
}
