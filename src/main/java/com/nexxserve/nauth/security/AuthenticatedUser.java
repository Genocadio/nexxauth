package com.nexxserve.nauth.security;

import com.nexxserve.nauth.entity.Permission;
import com.nexxserve.nauth.entity.Role;

import java.util.Set;

/**
 * Principal attached to the {@code SecurityContext} for platform-token
 * requests. Built by {@link JwtAuthenticationFilter} from the freshly loaded
 * user so disabled accounts and role changes take effect immediately. Also an
 * {@link OrgActor}: org endpoints accept platform tokens with their current
 * membership-based behaviour.
 */
public record AuthenticatedUser(Long id, String email, Role role, Long platformId) implements OrgActor {

    @Override
    public Long platformId() {
        return platformId;
    }

    @Override
    public Role platformRole() {
        return role;
    }

    @Override
    public Long organisationId() {
        return null;
    }

    @Override
    public Set<Permission> permissions() {
        return Set.of();
    }
}
