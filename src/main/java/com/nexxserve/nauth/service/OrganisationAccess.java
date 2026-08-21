package com.nexxserve.nauth.service;

import com.nexxserve.nauth.entity.Organisation;
import com.nexxserve.nauth.entity.Permission;
import com.nexxserve.nauth.entity.Platform;
import com.nexxserve.nauth.exception.ForbiddenException;
import com.nexxserve.nauth.exception.ResourceNotFoundException;
import com.nexxserve.nauth.repository.OrganisationRepository;
import com.nexxserve.nauth.security.OrgActor;
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

    public Organisation findOrganisationById(Long id) {
        return organisationRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Organisation", id));
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
