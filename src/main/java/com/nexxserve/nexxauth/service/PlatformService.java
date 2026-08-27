package com.nexxserve.nexxauth.service;

import com.nexxserve.nexxauth.dto.request.AddPlatformUserRequest;
import com.nexxserve.nexxauth.dto.response.PlatformResponse;
import com.nexxserve.nexxauth.dto.response.PlatformUserResponse;
import com.nexxserve.nexxauth.entity.LogCategory;
import com.nexxserve.nexxauth.entity.LogLevel;
import com.nexxserve.nexxauth.entity.Platform;
import com.nexxserve.nexxauth.entity.PlatformUser;
import com.nexxserve.nexxauth.entity.Role;
import com.nexxserve.nexxauth.exception.ConflictException;
import com.nexxserve.nexxauth.mapper.PlatformMapper;
import com.nexxserve.nexxauth.mapper.PlatformUserMapper;
import com.nexxserve.nexxauth.repository.PlatformRepository;
import com.nexxserve.nexxauth.repository.PlatformUserRepository;
import com.nexxserve.nexxauth.security.AuthenticatedUser;
import com.nexxserve.nexxauth.util.Emails;
import com.nexxserve.nexxauth.util.Slugs;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PlatformService {

    private final PlatformRepository platformRepository;
    private final PlatformUserRepository platformUserRepository;
    private final PlatformMapper platformMapper;
    private final PlatformUserMapper platformUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final PlatformAccess platformAccess;
    private final AuthAuditService audit;

    /** Public origin of this backend ({@code BACKEND_PUBLIC_URL}), used to
     * build the platform's copiable API base URL shown on the console. */
    @Value("${app.cors.backend-origin:}")
    private String backendOrigin;

    public PlatformService(PlatformRepository platformRepository, PlatformUserRepository platformUserRepository,
                           PlatformMapper platformMapper, PlatformUserMapper platformUserMapper,
                           PasswordEncoder passwordEncoder, PlatformAccess platformAccess,
                           AuthAuditService audit) {
        this.platformRepository = platformRepository;
        this.platformUserRepository = platformUserRepository;
        this.platformMapper = platformMapper;
        this.platformUserMapper = platformUserMapper;
        this.passwordEncoder = passwordEncoder;
        this.platformAccess = platformAccess;
        this.audit = audit;
    }

    /**
     * Creates the platform and its first user ({@code SUPER_USER}). Slug is
     * derived from the name when not provided and made unique if needed.
     */
    @Transactional
    public PlatformUser createPlatformWithOwner(String name, String slug, PlatformUser owner) {
        // The slug is optional on create: derived from the name when omitted.
        // An explicitly requested slug is never silently renamed - no two
        // platforms may share a slug, so a taken one is rejected outright.
        String resolvedSlug;
        boolean explicitSlug = slug != null && !slug.isBlank();
        if (explicitSlug) {
            if (platformRepository.existsBySlug(slug)) {
                throw new ConflictException("A platform with slug " + slug + " already exists");
            }
            resolvedSlug = slug;
        } else {
            resolvedSlug = Slugs.uniqueSlug(Slugs.slugify(name), platformRepository::existsBySlug);
        }

        Platform platform = new Platform();
        platform.setName(name);
        platform.setSlug(resolvedSlug);
        platformRepository.save(platform);

        owner.setPlatform(platform);
        owner.setRole(Role.SUPER_USER);
        return platformUserRepository.save(owner);
    }

    @Transactional(readOnly = true)
    public PlatformResponse getPlatform(String slug, AuthenticatedUser requester) {
        Platform platform = platformAccess.findPlatform(slug);
        platformAccess.requireMember(platform, requester);
        long userCount = platformUserRepository.countByPlatformId(platform.getId());
        return platformMapper.toResponse(platform, userCount, apiBaseUrl(platform.getSlug()));
    }

    /** {@code https://api.example.com/acme} — the public API base of a platform,
     * surfaced on the dashboards. Null when the backend's public origin is not
     * configured. */
    private String apiBaseUrl(String slug) {
        if (backendOrigin == null || backendOrigin.isBlank()) {
            return null;
        }
        return backendOrigin.replaceAll("/+$", "") + "/" + slug;
    }

    @Transactional(readOnly = true)
    public List<PlatformUserResponse> getUsers(String slug, AuthenticatedUser requester) {
        Platform platform = platformAccess.findPlatform(slug);
        platformAccess.requireMember(platform, requester);
        return platformUserRepository.findByPlatformIdOrderByCreatedAtAsc(platform.getId()).stream()
                .map(platformUserMapper::toResponse)
                .toList();
    }

    @Transactional
    public PlatformUserResponse addUser(String slug, AuthenticatedUser requester, AddPlatformUserRequest request) {
        Platform platform = platformAccess.findPlatform(slug);
        platformAccess.requireSuperUser(platform, requester);
        String email = Emails.normalize(request.email());
        if (platformUserRepository.existsByEmail(email)) {
            throw new ConflictException("A user with email " + email + " already exists");
        }
        PlatformUser user = platformUserMapper.toEntity(request);
        user.setEmail(email);
        user.setPlatform(platform);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole(request.role() != null ? request.role() : Role.READ_ONLY);
        PlatformUser saved = platformUserRepository.save(user);
        audit.logPersisted(LogLevel.INFO, LogCategory.USER_MANAGEMENT, AuthAuditService.PLATFORM_USER_ADDED,
                request.email(), null, null, saved.getRole().name());
        return platformUserMapper.toResponse(saved);
    }

}
