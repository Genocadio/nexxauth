package com.nexxserve.nexxauth.dto.request;

import com.nexxserve.nexxauth.entity.Permission;
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

        Set<Permission> permissions
) {
}
