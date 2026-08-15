package com.nexxserve.nexxauth.service;

import com.nexxserve.nexxauth.dto.request.UpdateUserRequest;
import com.nexxserve.nexxauth.dto.response.PlatformUserResponse;
import com.nexxserve.nexxauth.entity.PlatformUser;
import com.nexxserve.nexxauth.exception.ForbiddenException;
import com.nexxserve.nexxauth.exception.ResourceNotFoundException;
import com.nexxserve.nexxauth.mapper.PlatformUserMapper;
import com.nexxserve.nexxauth.repository.PlatformUserRepository;
import com.nexxserve.nexxauth.repository.RefreshTokenRepository;
import com.nexxserve.nexxauth.security.AuthenticatedUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class PlatformUserService {

    private final PlatformUserRepository platformUserRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PlatformUserMapper platformUserMapper;

    public PlatformUserService(PlatformUserRepository platformUserRepository,
                               RefreshTokenRepository refreshTokenRepository,
                               PlatformUserMapper platformUserMapper) {
        this.platformUserRepository = platformUserRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.platformUserMapper = platformUserMapper;
    }

    @Transactional(readOnly = true)
    public PlatformUserResponse getUser(Long id, AuthenticatedUser requester) {
        return platformUserMapper.toResponse(findInPlatform(id, requester.platformId()));
    }

    /**
     * Partial admin update of another user. A super user cannot demote or
     * disable themselves (prevents lockout).
     */
    @Transactional
    public PlatformUserResponse updateUser(Long id, AuthenticatedUser requester, UpdateUserRequest request) {
        PlatformUser target = findInPlatform(id, requester.platformId());
        boolean self = target.getId().equals(requester.id());

        if (request.firstName() != null) {
            target.setFirstName(request.firstName());
        }
        if (request.lastName() != null) {
            target.setLastName(request.lastName());
        }
        if (request.phone() != null) {
            target.setPhone(request.phone());
        }
        if (request.enabled() != null) {
            if (self && !request.enabled()) {
                throw new ForbiddenException("You cannot disable your own account");
            }
            target.setEnabled(request.enabled());
            if (!request.enabled()) {
                // Disabled accounts must not be able to refresh sessions.
                refreshTokenRepository.revokeAllForUser(target.getId(), Instant.now());
            }
        }
        if (request.role() != null) {
            if (self && request.role() != target.getRole()) {
                throw new ForbiddenException("You cannot change your own role");
            }
            target.setRole(request.role());
        }
        return platformUserMapper.toResponse(platformUserRepository.save(target));
    }

    private PlatformUser findInPlatform(Long id, Long platformId) {
        return platformUserRepository.findByIdAndPlatformId(id, platformId)
                .orElseThrow(() -> ResourceNotFoundException.of("User", id));
    }
}
