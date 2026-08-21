package com.nexxserve.nexxauth.dto.response;

import com.nexxserve.nexxauth.entity.Permission;

import java.util.Set;

public record OrganisationRoleResponse(
        Long id,
        String name,
        Set<Permission> permissions,
        /** When true, new users of the organisation inherit this role
         * automatically on register. */
        boolean isDefault
) {
}
