package com.nexxserve.nexxauth.security;

import com.nexxserve.nexxauth.entity.ClientType;
import com.nexxserve.nexxauth.entity.Permission;
import com.nexxserve.nexxauth.entity.Role;

import java.util.EnumSet;
import java.util.Set;

/**
 * Principal attached to the {@code SecurityContext} when a request is
 * authenticated by a client's static token (see {@link ClientTokenFilter}).
 * An authenticated client is treated like an org user holding every
 * organisation permission, but scoped to its own organisation (enforced by
 * {@link com.nexxserve.nexxauth.service.OrganisationAccess}).
 */
public record ClientPrincipal(Long id, String name, ClientType type, Long organisationId) implements OrgActor {

    private static final Set<Permission> ALL_PERMISSIONS = Set.copyOf(EnumSet.allOf(Permission.class));

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
        return ALL_PERMISSIONS;
    }
}
