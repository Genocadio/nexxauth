package com.nexxserve.nexxauth.service;

import com.nexxserve.nexxauth.dto.request.ChangePasswordRequest;
import com.nexxserve.nexxauth.dto.request.CreateOrganisationUserRequest;
import com.nexxserve.nexxauth.dto.request.UpdateOrganisationUserRequest;
import com.nexxserve.nexxauth.dto.request.UpdateOwnProfileRequest;
import com.nexxserve.nexxauth.dto.response.OrganisationUserResponse;
import com.nexxserve.nexxauth.entity.Organisation;
import com.nexxserve.nexxauth.entity.OrganisationRole;
import com.nexxserve.nexxauth.entity.OrganisationUser;
import com.nexxserve.nexxauth.entity.Permission;
import com.nexxserve.nexxauth.entity.Platform;
import com.nexxserve.nexxauth.exception.BadRequestException;
import com.nexxserve.nexxauth.exception.ConflictException;
import com.nexxserve.nexxauth.exception.ForbiddenException;
import com.nexxserve.nexxauth.exception.InvalidCredentialsException;
import com.nexxserve.nexxauth.exception.ResourceNotFoundException;
import com.nexxserve.nexxauth.mapper.OrganisationUserMapper;
import com.nexxserve.nexxauth.repository.OrganisationRoleRepository;
import com.nexxserve.nexxauth.repository.OrganisationUserRepository;
import com.nexxserve.nexxauth.security.OrgActor;
import com.nexxserve.nexxauth.security.OrgUserPrincipal;
import com.nexxserve.nexxauth.util.Emails;
import com.nexxserve.nexxauth.util.Usernames;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Organisation users: managed data, no authentication. A person may exist in
 * several organisations (one row each) and never outside one. Roles are
 * assigned at the organisation level only; users never hold permissions
 * directly.
 */
@Service
public class OrganisationUserService {

    private final OrganisationUserRepository userRepository;
    private final OrganisationRoleRepository roleRepository;
    private final PlatformAccess platformAccess;
    private final OrganisationAccess organisationAccess;
    private final OrganisationUserMapper userMapper;
    private final OrganisationAuthConfigService authConfigService;
    private final OrganisationRefreshTokenService refreshTokenService;
    private final OrganisationUserFieldService userFieldService;
    private final PasswordEncoder passwordEncoder;
    private final AuthAuditService audit;

    public OrganisationUserService(OrganisationUserRepository userRepository,
                                   OrganisationRoleRepository roleRepository, PlatformAccess platformAccess,
                                   OrganisationAccess organisationAccess, OrganisationUserMapper userMapper,
                                   OrganisationAuthConfigService authConfigService,
                                   OrganisationRefreshTokenService refreshTokenService,
                                   OrganisationUserFieldService userFieldService,
                                   PasswordEncoder passwordEncoder, AuthAuditService audit) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.platformAccess = platformAccess;
        this.organisationAccess = organisationAccess;
        this.userMapper = userMapper;
        this.authConfigService = authConfigService;
        this.refreshTokenService = refreshTokenService;
        this.userFieldService = userFieldService;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<OrganisationUserResponse> list(String platformSlug, String organisationSlug,
                                               OrgActor requester) {
        Organisation organisation = resolve(platformSlug, organisationSlug, requester, false);
        List<OrganisationUser> users =
                userRepository.findByOrganisationIdOrderByCreatedAtAsc(organisation.getId());
        Map<Long, Map<String, String>> metadata = userFieldService
                .readMetadataByUserIds(users.stream().map(OrganisationUser::getId).toList());
        return users.stream()
                .map(user -> userMapper.toResponse(user, metadata.getOrDefault(user.getId(), Map.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public OrganisationUserResponse get(String platformSlug, String organisationSlug, Long userId,
                                        OrgActor requester) {
        Organisation organisation = resolve(platformSlug, organisationSlug, requester, false);
        OrganisationUser user = findUser(organisation, userId);
        return userMapper.toResponse(user, userFieldService.readMetadata(user.getId()));
    }

    /** Every org user can read their own profile, regardless of permissions. */
    @Transactional(readOnly = true)
    public OrganisationUserResponse me(String platformSlug, String organisationSlug, OrgActor requester) {
        if (requester.isPlatformUser()) {
            throw new ForbiddenException("Platform users have no organisation profile");
        }
        Platform platform = platformAccess.findPlatform(platformSlug);
        Organisation organisation = organisationAccess.findOrganisation(platform, organisationSlug);
        organisationAccess.requireOrgUserOf(organisation, requester);
        OrganisationUser user = findUser(organisation,
                ((com.nexxserve.nexxauth.security.OrgUserPrincipal) requester).id());
        return userMapper.toResponse(user, userFieldService.readMetadata(user.getId()));
    }

    @Transactional
    public OrganisationUserResponse create(String platformSlug, String organisationSlug, OrgActor requester,
                                           CreateOrganisationUserRequest request) {
        Organisation organisation = resolve(platformSlug, organisationSlug, requester, true, Permission.ORGANISATION_USER_CREATE);
        String email = normalizedEmail(request.email());
        String username = cleanedUsername(request.username());

        if (organisation.isUseEmailAsUsername() && email == null) {
            throw new BadRequestException("Email is required as the username for this organisation");
        }
        assertIdentifiersFree(organisation, email, username, null);

        OrganisationUser user = userMapper.toEntity(request);
        user.setOrganisation(organisation);
        user.setEmail(email);
        user.setUsername(username);
        if (request.roleIds() != null) {
            user.setRoles(resolveRoles(organisation, request.roleIds()));
        }
        OrganisationUser saved = userRepository.save(user);
        if (request.metadata() != null) {
            userFieldService.setMetadata(saved, request.metadata());
        }
        if (request.password() != null && !request.password().isBlank()) {
            // A user created with a password gets the org's default auth type
            // (PASSWORD) and can log in; without one they have no auth yet.
            authConfigService.setPassword(saved, request.password());
            // A temporary password (set by the platform user) makes the user
            // change it at first login (CHANGE_PASSWORD action).
            saved.setTemporaryPassword(Boolean.TRUE.equals(request.temporaryPassword()));
        }
        return userMapper.toResponse(saved, userFieldService.readMetadata(saved.getId()));
    }

    @Transactional
    public OrganisationUserResponse update(String platformSlug, String organisationSlug, Long userId,
                                           OrgActor requester, UpdateOrganisationUserRequest request) {
        Organisation organisation = resolve(platformSlug, organisationSlug, requester, true, Permission.ORGANISATION_USER_UPDATE);
        OrganisationUser user = findUser(organisation, userId);

        if (request.firstName() != null) {
            user.setFirstName(request.firstName());
        }
        if (request.lastName() != null) {
            user.setLastName(request.lastName());
        }
        if (request.email() != null) {
            String email = normalizedEmail(request.email());
            if (organisation.isUseEmailAsUsername() && email == null) {
                throw new BadRequestException("Email is required as the username for this organisation");
            }
            assertIdentifiersFree(organisation, email, user.getUsername(), user);
            user.setEmail(email);
        }
        if (request.username() != null) {
            String username = cleanedUsername(request.username());
            assertIdentifiersFree(organisation, user.getEmail(), username, user);
            user.setUsername(username);
        }
        if (request.enabled() != null) {
            user.setEnabled(request.enabled());
            if (!request.enabled()) {
                // A disabled account must not be able to refresh sessions.
                refreshTokenService.revokeAllForUser(user.getId());
            }
        }
        if (request.roleIds() != null) {
            user.setRoles(resolveRoles(organisation, request.roleIds()));
        }
        if (request.password() != null) {
            if (request.password().isBlank()) {
                // Empty string clears auth: the user can no longer log in.
                authConfigService.clearAuth(user);
            } else {
                authConfigService.setPassword(user, request.password());
            }
            // An admin password change (reset) must force re-authentication:
            // revoke every outstanding refresh token so existing sessions die
            // with the old password.
            refreshTokenService.revokeAllForUser(user.getId());
        }
        if (request.temporaryPassword() != null) {
            user.setTemporaryPassword(request.temporaryPassword());
            if (request.temporaryPassword()) {
                // Triggering a forced password change kills existing sessions so
                // the user re-authenticates into the gated action flow (fixed
                // 5-minute access, no refresh, only the change-password endpoint)
                // and cannot keep working under the old password.
                refreshTokenService.revokeAllForUser(user.getId());
            }
        }
        if (request.metadata() != null) {
            userFieldService.setMetadata(user, request.metadata());
        }
        return userMapper.toResponse(userRepository.save(user), userFieldService.readMetadata(user.getId()));
    }

    @Transactional
    public void delete(String platformSlug, String organisationSlug, Long userId, OrgActor requester) {
        Organisation organisation = resolve(platformSlug, organisationSlug, requester, true, Permission.ORGANISATION_USER_DELETE);
        userRepository.delete(findUser(organisation, userId));
    }

    /** Self-service password change (any org user, regardless of permissions).
     * Completes the CHANGE_PASSWORD action: the temporary flag is cleared so the
     * next login issues a full session. */
    @Transactional
    public void changePassword(String platformSlug, String organisationSlug, OrgActor requester,
                               ChangePasswordRequest request) {
        OrganisationUser user = ownUser(platformSlug, organisationSlug, requester);
        String hash = user.getPasswordHash();
        if (hash == null || !passwordEncoder.matches(request.currentPassword(), hash)) {
            throw new InvalidCredentialsException();
        }
        // Validated against the org's password rules (length + reuse history).
        authConfigService.setPassword(user, request.newPassword());
        user.setTemporaryPassword(false);
        userRepository.save(user);
        // Force re-authentication: revoke every outstanding refresh token so no
        // session survives under the old password.
        refreshTokenService.revokeAllForUser(user.getId());
        audit.log(AuthAuditService.ORG_PASSWORD_CHANGED,
                identifierOf(user), user.getOrganisation().getSlug());
    }

    /** Self-service partial profile update (any org user, regardless of
     * permissions). Used to complete the UPDATE_PROFILE action, e.g. filling
     * values for required organisation user fields. */
    @Transactional
    public OrganisationUserResponse updateOwnProfile(String platformSlug, String organisationSlug,
                                                     OrgActor requester, UpdateOwnProfileRequest request) {
        OrganisationUser user = ownUser(platformSlug, organisationSlug, requester);
        if (request.firstName() != null) {
            user.setFirstName(request.firstName());
        }
        if (request.lastName() != null) {
            user.setLastName(request.lastName());
        }
        if (request.metadata() != null) {
            userFieldService.setMetadata(user, request.metadata());
        }
        return userMapper.toResponse(userRepository.save(user), userFieldService.readMetadata(user.getId()));
    }

    /** Resolves the requesting org user's own account, forbidding platform
     * users (they have no organisation profile). */
    private OrganisationUser ownUser(String platformSlug, String organisationSlug, OrgActor requester) {
        if (requester.isPlatformUser()) {
            throw new ForbiddenException("Platform users have no organisation profile");
        }
        Platform platform = platformAccess.findPlatform(platformSlug);
        Organisation organisation = organisationAccess.findOrganisation(platform, organisationSlug);
        organisationAccess.requireOrgUserOf(organisation, requester);
        return findUser(organisation, ((OrgUserPrincipal) requester).id());
    }

    private String identifierOf(OrganisationUser user) {
        return user.getUsername() != null ? user.getUsername() : user.getEmail();
    }

    private void assertIdentifiersFree(Organisation organisation, String email, String username,
                                       OrganisationUser exclude) {
        if (email != null && (exclude == null || !email.equals(exclude.getEmail()))
                && userRepository.existsByOrganisationIdAndEmail(organisation.getId(), email)) {
            throw new ConflictException("An organisation user with email " + email
                    + " already exists in this organisation");
        }
        if (username != null && (exclude == null || !username.equals(exclude.getUsername()))
                && userRepository.existsByOrganisationIdAndUsername(organisation.getId(), username)) {
            throw new ConflictException("An organisation user with username " + username
                    + " already exists in this organisation");
        }
    }

    private Set<OrganisationRole> resolveRoles(Organisation organisation, Set<Long> roleIds) {
        // Mutable set: Hibernate mutates the collection when syncing the join
        // table, and Set.of()/List.of() are immutable (UnsupportedOperationException).
        Set<OrganisationRole> roles = new java.util.HashSet<>();
        if (!roleIds.isEmpty()) {
            Set<OrganisationRole> found = roleRepository.findByIdInAndOrganisationId(roleIds, organisation.getId());
            if (found.size() != roleIds.size()) {
                throw new BadRequestException("One or more roles do not belong to this organisation");
            }
            roles.addAll(found);
        }
        return roles;
    }

    private OrganisationUser findUser(Organisation organisation, Long userId) {
        return userRepository.findByIdAndOrganisationId(userId, organisation.getId())
                .orElseThrow(() -> ResourceNotFoundException.of("Organisation user", userId));
    }

    private Organisation resolve(String platformSlug, String organisationSlug, OrgActor requester,
                                 boolean write) {
        return resolve(platformSlug, organisationSlug, requester, write, null);
    }

    private Organisation resolve(String platformSlug, String organisationSlug, OrgActor requester,
                                 boolean write, Permission permission) {
        Platform platform = platformAccess.findPlatform(platformSlug);
        Organisation organisation = organisationAccess.findOrganisation(platform, organisationSlug);
        if (write) {
            if (permission == null) {
                platformAccess.requireSuperUser(platform, requester);
            } else {
                organisationAccess.requireWrite(platform, organisation, requester, permission);
            }
        } else {
            organisationAccess.requireRead(platform, organisation, requester);
        }
        return organisation;
    }

    /** null/blank -> null (clears the identifier); otherwise trimmed + normalized. */
    private String normalizedEmail(String email) {
        if (email == null) {
            return null;
        }
        String normalized = Emails.normalize(email);
        return normalized.isBlank() ? null : normalized;
    }

    /** null/blank -> null (clears the identifier); otherwise trimmed + lowercased
     * so Bob and bob are the same account. */
    private String cleanedUsername(String value) {
        if (value == null) {
            return null;
        }
        String normalized = Usernames.normalize(value);
        return normalized.isEmpty() ? null : normalized;
    }
}
