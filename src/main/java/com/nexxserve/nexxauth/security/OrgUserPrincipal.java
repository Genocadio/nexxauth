package com.nexxserve.nexxauth.security;

import com.nexxserve.nexxauth.entity.Permission;
import com.nexxserve.nexxauth.entity.Role;

import java.util.Set;

/**
 * Principal attached to the {@code SecurityContext} for organisation-token
 * requests. Built by {@link OrgJwtAuthenticationFilter} from the freshly loaded
 * org user (with its roles eagerly loaded), so role/permission changes take
 * effect immediately instead of trusting token claims for the whole lifetime.
 */
public record OrgUserPrincipal(Long id, Long organisationId, Set<Permission> permissions) implements OrgActor {

    @Override
    public Long platformId() {
        return null;
    }

    @Override
    public Role platformRole() {
        return null;
    }

    @Override
    public Long organisationId() {
        return organisationId;
    }

    @Override
    public Set<Permission> permissions() {
        return permissions;
    }
}
