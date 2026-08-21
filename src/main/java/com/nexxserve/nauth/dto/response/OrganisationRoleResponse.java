package com.nexxserve.nauth.dto.response;

import com.nexxserve.nauth.entity.Permission;

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
