package com.nexxserve.nexxauth.security;

import com.nexxserve.nexxauth.entity.Permission;
import com.nexxserve.nexxauth.entity.Role;

import java.util.Set;

/**
 * Who is acting on an organisation endpoint: either a platform user (with a
 * platform membership role) or an organisation user (with org-role
 * permissions). Org endpoints accept both kinds of tokens - platform users keep
 * the current behaviour, org users are gated by their role's permissions.
 */
public interface OrgActor {

    /** Platform user's platform id, or {@code null} for org users. */
    Long platformId();

    /** Platform user's role, or {@code null} for org users. */
    Role platformRole();

    /** Org user's organisation id, or {@code null} for platform users. */
    Long organisationId();

    /** Org user's effective permissions (empty for platform users). */
    Set<Permission> permissions();

    default boolean isPlatformUser() {
        return platformId() != null;
    }
}
