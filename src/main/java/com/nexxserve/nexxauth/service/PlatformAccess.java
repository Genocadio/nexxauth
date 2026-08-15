package com.nexxserve.nexxauth.service;

import com.nexxserve.nexxauth.entity.Platform;
import com.nexxserve.nexxauth.entity.Role;
import com.nexxserve.nexxauth.exception.ForbiddenException;
import com.nexxserve.nexxauth.exception.ResourceNotFoundException;
import com.nexxserve.nexxauth.repository.PlatformRepository;
import com.nexxserve.nexxauth.security.OrgActor;
import org.springframework.stereotype.Component;

/**
 * Shared platform lookup and access checks so every platform-scoped service
 * enforces the same membership rules without repetition.
 */
@Component
public class PlatformAccess {

    private final PlatformRepository platformRepository;

    public PlatformAccess(PlatformRepository platformRepository) {
        this.platformRepository = platformRepository;
    }

    public Platform findPlatform(String slug) {
        return platformRepository.findBySlug(slug)
                .orElseThrow(() -> ResourceNotFoundException.of("Platform", slug));
    }

    /** Any member of the platform may read its data. */
    public void requireMember(Platform platform, OrgActor requester) {
        if (!platform.getId().equals(requester.platformId())) {
            throw new ForbiddenException("You do not have access to this platform");
        }
    }

    /** Writes additionally require the super user role. */
    public void requireSuperUser(Platform platform, OrgActor requester) {
        requireMember(platform, requester);
        if (requester.platformRole() != Role.SUPER_USER) {
            throw new ForbiddenException("Only super users can perform this action");
        }
    }
}
