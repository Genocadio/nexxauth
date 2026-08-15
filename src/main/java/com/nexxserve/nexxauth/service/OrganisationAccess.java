package com.nexxserve.nexxauth.service;

import com.nexxserve.nexxauth.entity.Organisation;
import com.nexxserve.nexxauth.entity.Permission;
import com.nexxserve.nexxauth.entity.Platform;
import com.nexxserve.nexxauth.exception.ForbiddenException;
import com.nexxserve.nexxauth.exception.ResourceNotFoundException;
import com.nexxserve.nexxauth.repository.OrganisationRepository;
import com.nexxserve.nexxauth.security.OrgActor;
import org.springframework.stereotype.Component;

/**
 * Shared lookup of organisations under a platform and the access checks for
 * organisation endpoints. Both kinds of tokens are accepted: platform users
 * keep their membership-based behaviour (member reads, super user writes);
 * organisation users must belong to the organisation and hold the required
 * role permission.
 */
@Component
public class OrganisationAccess {

    private final OrganisationRepository organisationRepository;
    private final PlatformAccess platformAccess;

    public OrganisationAccess(OrganisationRepository organisationRepository, PlatformAccess platformAccess) {
        this.organisationRepository = organisationRepository;
        this.platformAccess = platformAccess;
    }

    public Organisation findOrganisation(Platform platform, String slug) {
        return organisationRepository.findByPlatformIdAndSlug(platform.getId(), slug)
                .orElseThrow(() -> ResourceNotFoundException.of("Organisation", slug));
    }

    /** Read access: platform members, or org users of this organisation with the
     * read permission. */
    public void requireRead(Platform platform, Organisation organisation, OrgActor actor) {
        requireRead(platform, organisation, actor, Permission.ORGANISATION_USER_READ);
    }

    /** Read access with a specific permission for org users (platform members
     * still read freely). */
    public void requireRead(Platform platform, Organisation organisation, OrgActor actor,
                            Permission permission) {
        if (actor.isPlatformUser()) {
            platformAccess.requireMember(platform, actor);
        } else {
            requireOrgUser(organisation, actor);
            requirePermission(actor, permission);
        }
    }

    /** Write access: platform super users, or org users of this organisation
     * with the specific write permission. */
    public void requireWrite(Platform platform, Organisation organisation, OrgActor actor, Permission permission) {
        if (actor.isPlatformUser()) {
            platformAccess.requireSuperUser(platform, actor);
        } else {
            requireOrgUser(organisation, actor);
            requirePermission(actor, permission);
        }
    }

    /** An org user of this organisation may read its own profile regardless of
     * permissions ("every user can read himself"). */
    public void requireOrgUserOf(Organisation organisation, OrgActor actor) {
        requireOrgUser(organisation, actor);
    }

    private void requireOrgUser(Organisation organisation, OrgActor actor) {
        if (actor.organisationId() == null || !organisation.getId().equals(actor.organisationId())) {
            throw new ForbiddenException("You do not have access to this organisation");
        }
    }

    private void requirePermission(OrgActor actor, Permission permission) {
        if (!actor.permissions().contains(permission)) {
            throw new ForbiddenException("Missing permission: " + permission);
        }
    }
}
