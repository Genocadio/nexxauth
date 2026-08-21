package com.nexxserve.nauth.service;

import com.nexxserve.nauth.dto.request.ChangePasswordRequest;
import com.nexxserve.nauth.dto.request.CreateOrganisationUserRequest;
import com.nexxserve.nauth.dto.request.UpdateOrganisationUserRequest;
import com.nexxserve.nauth.dto.request.UpdateOwnProfileRequest;
import com.nexxserve.nauth.dto.response.OrganisationUserResponse;
import com.nexxserve.nauth.entity.Organisation;
import com.nexxserve.nauth.entity.OrganisationRole;
import com.nexxserve.nauth.entity.OrganisationUser;
import com.nexxserve.nauth.entity.Permission;
import com.nexxserve.nauth.entity.Platform;
import com.nexxserve.nauth.exception.BadRequestException;
import com.nexxserve.nauth.exception.ConflictException;
import com.nexxserve.nauth.exception.ForbiddenException;
import com.nexxserve.nauth.exception.InvalidCredentialsException;
import com.nexxserve.nauth.exception.ResourceNotFoundException;
import com.nexxserve.nauth.mapper.OrganisationUserMapper;
import com.nexxserve.nauth.repository.OrganisationRoleRepository;
import com.nexxserve.nauth.repository.OrganisationUserRepository;
import com.nexxserve.nauth.security.OrgActor;
import com.nexxserve.nauth.security.OrgUserPrincipal;
import com.nexxserve.nauth.util.Emails;
import com.nexxserve.nauth.util.Phones;
import com.nexxserve.nauth.util.Usernames;
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
    public List<OrganisationUserResponse> list(String platformSlug, Long organisationId,
                                               OrgActor requester) {
        Organisation organisation = resolve(platformSlug, organisationId, requester, false);
        List<OrganisationUser> users =
                userRepository.findByOrganisationIdOrderByCreatedAtAsc(organisation.getId());
        Map<Long, Map<String, String>> metadata = userFieldService
                .readMetadataByUserIds(users.stream().map(OrganisationUser::getId).toList());
        return users.stream()
                .map(user -> userMapper.toResponse(user, metadata.getOrDefault(user.getId(), Map.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public OrganisationUserResponse get(String platformSlug, Long organisationId, Long userId,
                                        OrgActor requester) {
        Organisation organisation = resolve(platformSlug, organisationId, requester, false);
        OrganisationUser user = findUser(organisation, userId);
        return userMapper.toResponse(user, userFieldService.readMetadata(user.getId()));
    }

    /** Every org user can read their own profile, regardless of permissions. */
    @Transactional(readOnly = true)
    public OrganisationUserResponse me(String platformSlug, Long organisationId, OrgActor requester) {
        if (requester.isPlatformUser()) {
            throw new ForbiddenException("Platform users have no organisation profile");
        }
        Platform platform = platformAccess.findPlatform(platformSlug);
        Organisation organisation = organisationAccess.findOrganisationById(organisationId);
        organisationAccess.requireOrgUserOf(organisation, requester);
        OrganisationUser user = findUser(organisation,
                ((com.nexxserve.nauth.security.OrgUserPrincipal) requester).id());
        return userMapper.toResponse(user, userFieldService.readMetadata(user.getId()));
    }

    @Transactional
    public OrganisationUserResponse create(String platformSlug, Long organisationId, OrgActor requester,
                                           CreateOrganisationUserRequest request) {
        Organisation organisation = resolve(platformSlug, organisationId, requester, true, Permission.ORGANISATION_USER_CREATE);
        String email = normalizedEmail(request.email());
        String username = cleanedUsername(request.username());
        String phone = cleanedPhone(request.phone());

        // Admin-created users may be placeholders without a username or phone
        // (they simply cannot log in until one is added); email is still
        // enforced when it is the required login identifier, mirroring the
        // legacy email-as-username rule.
        if (organisation.isEmailRequired() && email == null) {
            throw new BadRequestException("Email is required for this organisation");
        }
        assertIdentifiersFree(organisation, email, username, phone, null);

        OrganisationUser user = userMapper.toEntity(request);
        user.setOrganisation(organisation);
        user.setEmail(email);
        user.setUsername(username);
        user.setPhone(phone);
        user.setLastName(cleanedName(request.lastName()));
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
    public OrganisationUserResponse update(String platformSlug, Long organisationId, Long userId,
                                           OrgActor requester, UpdateOrganisationUserRequest request) {
        Organisation organisation = resolve(platformSlug, organisationId, requester, true, Permission.ORGANISATION_USER_UPDATE);
        OrganisationUser user = findUser(organisation, userId);

        if (request.firstName() != null) {
            user.setFirstName(request.firstName());
        }
        if (request.lastName() != null) {
            // Blank clears the (now optional) last name.
            user.setLastName(cleanedName(request.lastName()));
        }
        if (request.email() != null) {
            String email = normalizedEmail(request.email());
            if (organisation.isEmailRequired() && email == null) {
                throw new BadRequestException("Email is required for this organisation");
            }
            assertIdentifiersFree(organisation, email, user.getUsername(), user.getPhone(), user);
            user.setEmail(email);
        }
        if (request.username() != null) {
            String username = cleanedUsername(request.username());
            if (organisation.isUsernameRequired() && username == null) {
                throw new BadRequestException("Username is required for this organisation");
            }
            assertIdentifiersFree(organisation, user.getEmail(), username, user.getPhone(), user);
            user.setUsername(username);
        }
        if (request.phone() != null) {
            String phone = cleanedPhone(request.phone());
            if (organisation.isPhoneRequired() && phone == null) {
                throw new BadRequestException("Phone is required for this organisation");
            }
            assertIdentifiersFree(organisation, user.getEmail(), user.getUsername(), phone, user);
            user.setPhone(phone);
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
    public void delete(String platformSlug, Long organisationId, Long userId, OrgActor requester) {
        Organisation organisation = resolve(platformSlug, organisationId, requester, true, Permission.ORGANISATION_USER_DELETE);
        userRepository.delete(findUser(organisation, userId));
    }

    /** Self-service password change (any org user, regardless of permissions).
     * Completes the CHANGE_PASSWORD action: the temporary flag is cleared so the
     * next login issues a full session. */
    @Transactional
    public void changePassword(String platformSlug, Long organisationId, OrgActor requester,
                               ChangePasswordRequest request) {
        OrganisationUser user = ownUser(platformSlug, organisationId, requester);
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
    public OrganisationUserResponse updateOwnProfile(String platformSlug, Long organisationId,
                                                     OrgActor requester, UpdateOwnProfileRequest request) {
        OrganisationUser user = ownUser(platformSlug, organisationId, requester);
        if (request.firstName() != null) {
            user.setFirstName(request.firstName());
        }
        if (request.lastName() != null) {
            // Blank clears the (now optional) last name.
            user.setLastName(cleanedName(request.lastName()));
        }
        if (request.metadata() != null) {
            userFieldService.setMetadata(user, request.metadata());
        }
        return userMapper.toResponse(userRepository.save(user), userFieldService.readMetadata(user.getId()));
    }

    /** Resolves the requesting org user's own account, forbidding platform
     * users (they have no organisation profile). */
    private OrganisationUser ownUser(String platformSlug, Long organisationId, OrgActor requester) {
        if (requester.isPlatformUser()) {
            throw new ForbiddenException("Platform users have no organisation profile");
        }
        Platform platform = platformAccess.findPlatform(platformSlug);
        Organisation organisation = organisationAccess.findOrganisationById(organisationId);
        organisationAccess.requireOrgUserOf(organisation, requester);
        return findUser(organisation, ((OrgUserPrincipal) requester).id());
    }

    private String identifierOf(OrganisationUser user) {
        // First non-null identifier, so phone-only users stay attributable.
        return user.getUsername() != null ? user.getUsername()
                : user.getEmail() != null ? user.getEmail()
                : user.getPhone() != null ? user.getPhone() : "unknown";
    }

    private void assertIdentifiersFree(Organisation organisation, String email, String username,
                                       String phone, OrganisationUser exclude) {
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
        if (phone != null && (exclude == null || !phone.equals(exclude.getPhone()))
                && userRepository.existsByOrganisationIdAndPhone(organisation.getId(), phone)) {
            throw new ConflictException("An organisation user with phone " + phone
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

    private Organisation resolve(String platformSlug, Long organisationId, OrgActor requester,
                                 boolean write) {
        return resolve(platformSlug, organisationId, requester, write, null);
    }

    private Organisation resolve(String platformSlug, Long organisationId, OrgActor requester,
                                 boolean write, Permission permission) {
        Platform platform = platformAccess.findPlatform(platformSlug);
        Organisation organisation = organisationAccess.findOrganisationById(organisationId);
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

    /** null/blank -> null; otherwise trimmed. */
    private String cleanedName(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
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

    /** null/blank -> null (clears the identifier); otherwise trimmed with
     * separators stripped so +1 (555) 123-4567 == +15551234567. */
    private String cleanedPhone(String value) {
        if (value == null) {
            return null;
        }
        String normalized = Phones.normalize(value);
        return normalized.isEmpty() ? null : normalized;
    }
}
