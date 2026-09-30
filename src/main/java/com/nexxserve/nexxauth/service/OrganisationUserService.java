package com.nexxserve.nexxauth.service;

import com.nexxserve.nexxauth.dto.request.ChangePasswordRequest;
import com.nexxserve.nexxauth.dto.request.CreateOrganisationUserRequest;
import com.nexxserve.nexxauth.dto.request.UpdateOrganisationUserRequest;
import com.nexxserve.nexxauth.dto.request.UpdateOwnProfileRequest;
import com.nexxserve.nexxauth.dto.response.OrganisationUserResponse;
import com.nexxserve.nexxauth.entity.LogCategory;
import com.nexxserve.nexxauth.entity.LogLevel;
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
import com.nexxserve.nexxauth.util.Phones;
import com.nexxserve.nexxauth.util.Usernames;
import com.nexxserve.nexxauth.dto.request.AddUserEmailRequest;
import com.nexxserve.nexxauth.dto.request.AddUserPhoneRequest;
import com.nexxserve.nexxauth.dto.request.SendPasswordResetRequest;
import com.nexxserve.nexxauth.dto.request.SendUserVerificationRequest;
import com.nexxserve.nexxauth.dto.response.VerificationRequestResponse;
import com.nexxserve.nexxauth.entity.OrganisationUserEmail;
import com.nexxserve.nexxauth.entity.OrganisationUserPhone;
import com.nexxserve.nexxauth.entity.UserLoginMethod;
import com.nexxserve.nexxauth.entity.VerificationChannel;
import com.nexxserve.nexxauth.entity.VerificationPurpose;
import com.nexxserve.nexxauth.repository.OrganisationUserEmailRepository;
import com.nexxserve.nexxauth.repository.OrganisationUserPhoneRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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
    private final OrganisationUserEmailRepository emailRepository;
    private final OrganisationUserPhoneRepository phoneRepository;
    private final PlatformAccess platformAccess;
    private final OrganisationAccess organisationAccess;
    private final OrganisationUserMapper userMapper;
    private final OrganisationAuthConfigService authConfigService;
    private final OrganisationRefreshTokenService refreshTokenService;
    private final OrganisationUserFieldService userFieldService;
    private final OrganisationVerificationService verificationService;
    private final PasswordEncoder passwordEncoder;
    private final AuthAuditService audit;

    public OrganisationUserService(OrganisationUserRepository userRepository,
                                   OrganisationRoleRepository roleRepository,
                                   OrganisationUserEmailRepository emailRepository,
                                   OrganisationUserPhoneRepository phoneRepository,
                                   PlatformAccess platformAccess,
                                   OrganisationAccess organisationAccess, OrganisationUserMapper userMapper,
                                   OrganisationAuthConfigService authConfigService,
                                   OrganisationRefreshTokenService refreshTokenService,
                                   OrganisationUserFieldService userFieldService,
                                   OrganisationVerificationService verificationService,
                                   PasswordEncoder passwordEncoder, AuthAuditService audit) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.emailRepository = emailRepository;
        this.phoneRepository = phoneRepository;
        this.platformAccess = platformAccess;
        this.organisationAccess = organisationAccess;
        this.userMapper = userMapper;
        this.authConfigService = authConfigService;
        this.refreshTokenService = refreshTokenService;
        this.userFieldService = userFieldService;
        this.verificationService = verificationService;
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
                ((OrgUserPrincipal) requester).id());
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
        user.setUsername(username);
        user.setLastName(cleanedName(request.lastName()));
        if (email != null) {
            user.addEmail(email, true, null);
        }
        if (phone != null) {
            user.addPhone(phone, true, null);
        }
        if (request.roleIds() != null) {
            user.setRoles(resolveRoles(organisation, request.roleIds()));
        }
        // A user created with no password is a placeholder: it has never been
        // given a way in, so it stays inert (PASSWORD with no hash means no
        // usable credential) until an admin configures it. This is deliberately
        // different from *removing* the password of a live user, which switches
        // them to OTP so they can still sign in.
        boolean hasPassword = request.password() != null && !request.password().isBlank();
        if (request.loginMethod() != null) {
            user.setLoginMethod(request.loginMethod());
        } else if (!hasPassword) {
            user.setLoginMethod(UserLoginMethod.PASSWORD);
        }
        OrganisationUser saved = userRepository.save(user);
        if (request.metadata() != null) {
            userFieldService.setMetadata(saved, request.metadata());
        }
        saved.bumpDataHash();
        if (request.password() != null && !request.password().isBlank()) {
            // A user created with a password gets the org's default auth type
            // (PASSWORD) and can log in; without one they have no auth yet.
            authConfigService.setPassword(saved, request.password());
            // A temporary password (set by the platform user) makes the user
            // change it at first login (CHANGE_PASSWORD action).
            saved.setTemporaryPassword(Boolean.TRUE.equals(request.temporaryPassword()));
        }
        audit.logPersisted(LogLevel.INFO, LogCategory.USER_MANAGEMENT, AuthAuditService.ORG_USER_CREATED,
                identifierOf(saved), organisation.getSlug(), organisation.getId(), null);
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
            assertIdentifiersFree(organisation, email, user.getUsername(), user.getPrimaryPhone(), user);
            if (email == null) {
                user.getEmails().clear();
            } else {
                user.addEmail(email, true, null);
            }
        }
        if (request.username() != null) {
            String username = cleanedUsername(request.username());
            if (organisation.isUsernameRequired() && username == null) {
                throw new BadRequestException("Username is required for this organisation");
            }
            assertIdentifiersFree(organisation, user.getPrimaryEmail(), username, user.getPrimaryPhone(), user);
            user.setUsername(username);
        }
        if (request.phone() != null) {
            String phone = cleanedPhone(request.phone());
            if (organisation.isPhoneRequired() && phone == null) {
                throw new BadRequestException("Phone is required for this organisation");
            }
            assertIdentifiersFree(organisation, user.getPrimaryEmail(), user.getUsername(), phone, user);
            if (phone == null) {
                user.getPhones().clear();
            } else {
                user.addPhone(phone, true, null);
            }
        }

        if (request.enabled() != null && request.enabled() != user.isEnabled()) {
            user.setEnabled(request.enabled());
            if (!request.enabled()) {
                refreshTokenService.revokeAllForUser(user.getId());
            }
            audit.logPersisted(LogLevel.INFO, LogCategory.USER_MANAGEMENT,
                    request.enabled() ? AuthAuditService.ORG_USER_ENABLED : AuthAuditService.ORG_USER_DISABLED,
                    identifierOf(user), organisation.getSlug(), organisation.getId(), null);
        }
        Set<Long> previousRoleIds = user.getRoles().stream().map(OrganisationRole::getId).collect(Collectors.toSet());
        if (request.roleIds() != null) {
            user.setRoles(resolveRoles(organisation, request.roleIds()));
        }
        UserLoginMethod previousLoginMethod = user.getLoginMethod();
        if (request.loginMethod() != null) {
            user.setLoginMethod(request.loginMethod());
        }
        if (request.password() != null) {
            if (request.password().isBlank()) {
                authConfigService.clearAuth(user);
                // Clearing the password used to strand the user with no method at
                // all. Default to OTP so "remove password" means "sign in with a
                // code"; an explicit loginMethod in the same request still wins
                // because it was applied above.
                if (request.loginMethod() == null) {
                    user.setLoginMethod(UserLoginMethod.OTP);
                }
            } else {
                authConfigService.setPassword(user, request.password());
            }
            refreshTokenService.revokeAllForUser(user.getId());
            audit.logPersisted(LogLevel.WARN, LogCategory.SECURITY, AuthAuditService.ORG_USER_PASSWORD_RESET,
                    identifierOf(user), organisation.getSlug(), organisation.getId(), null);
        }
        if (previousLoginMethod != null && previousLoginMethod != user.getLoginMethod()) {
            // Changing what a user may sign in with must not leave an existing
            // session valid under the old rules.
            refreshTokenService.revokeAllForUser(user.getId());
            audit.logPersisted(LogLevel.WARN, LogCategory.SECURITY,
                    AuthAuditService.ORG_USER_LOGIN_METHOD_CHANGED,
                    identifierOf(user), organisation.getSlug(), organisation.getId(),
                    previousLoginMethod.name() + " -> " + user.getLoginMethod().name());
        }
        if (user.getLoginMethod() != null && !user.getLoginMethod().allowsPassword()
                && user.getPasswordHash() != null) {
            // OTP-only with a stored password would let the password keep working
            // wherever a stale request is replayed; drop it so the stored
            // credential matches what the account is allowed to use.
            authConfigService.clearAuth(user);
        }
        if (request.temporaryPassword() != null) {
            user.setTemporaryPassword(request.temporaryPassword());
            if (request.temporaryPassword()) {
                refreshTokenService.revokeAllForUser(user.getId());
            }
        }
        if (request.requireEmailVerificationAtNextLogin() != null) {
            user.setRequireEmailVerificationAtNextLogin(request.requireEmailVerificationAtNextLogin());
            if (request.requireEmailVerificationAtNextLogin()) {
                refreshTokenService.revokeAllForUser(user.getId());
            }
        }
        if (request.requirePhoneVerificationAtNextLogin() != null) {
            user.setRequirePhoneVerificationAtNextLogin(request.requirePhoneVerificationAtNextLogin());
            if (request.requirePhoneVerificationAtNextLogin()) {
                refreshTokenService.revokeAllForUser(user.getId());
            }
        }
        if (request.metadata() != null) {
            userFieldService.setMetadata(user, request.metadata());
        }
        user.bumpDataHash();
        OrganisationUser saved = userRepository.save(user);
        Set<Long> newRoleIds = saved.getRoles().stream().map(OrganisationRole::getId).collect(Collectors.toSet());
        if (!previousRoleIds.equals(newRoleIds)) {
            audit.logPersisted(LogLevel.INFO, LogCategory.USER_MANAGEMENT, AuthAuditService.ORG_USER_ROLES_CHANGED,
                    identifierOf(saved), organisation.getSlug(), organisation.getId(),
                    "roles=" + newRoleIds);
        }
        audit.logPersisted(LogLevel.INFO, LogCategory.USER_MANAGEMENT, AuthAuditService.ORG_USER_UPDATED,
                identifierOf(saved), organisation.getSlug(), organisation.getId(), null);
        return userMapper.toResponse(saved, userFieldService.readMetadata(saved.getId()));
    }

    @Transactional
    public void delete(String platformSlug, Long organisationId, Long userId, OrgActor requester) {
        Organisation organisation = resolve(platformSlug, organisationId, requester, true, Permission.ORGANISATION_USER_DELETE);
        OrganisationUser user = findUser(organisation, userId);
        audit.logPersisted(LogLevel.INFO, LogCategory.USER_MANAGEMENT, AuthAuditService.ORG_USER_DELETED,
                identifierOf(user), organisation.getSlug(), organisation.getId(), null);
        userRepository.delete(user);
    }

    /** Self-service password change (any org user, regardless of permissions).
     * Completes the CHANGE_PASSWORD action: the temporary flag is cleared so the
     * next login issues a full session. */
    @Transactional
    public void changePassword(String platformSlug, Long organisationId, OrgActor requester,
                               ChangePasswordRequest request) {
        OrganisationUser user = ownUser(platformSlug, organisationId, requester);
        if (user.getPasswordHash() != null && !passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        authConfigService.setPassword(user, request.newPassword());
        user.setTemporaryPassword(false);
        userRepository.save(user);
        // Invalidate all other sessions so stolen tokens can't ride on the password change.
        refreshTokenService.revokeAllForUser(user.getId());
        audit.logPersisted(LogLevel.INFO, LogCategory.AUTH, AuthAuditService.ORG_PASSWORD_CHANGED,
                identifierOf(user), user.getOrganisation().getSlug(), user.getOrganisation().getId(), null);
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
        user.bumpDataHash();
        return userMapper.toResponse(userRepository.save(user), userFieldService.readMetadata(user.getId()));
    }

    @Transactional
    public OrganisationUserResponse addEmail(String platformSlug, Long organisationId, Long userId,
                                             OrgActor requester, AddUserEmailRequest request) {
        Organisation organisation = resolveForUser(platformSlug, organisationId, userId, requester);
        OrganisationUser user = findUser(organisation, userId);
        String email = normalizedEmail(request.email());
        if (email == null) {
            throw new BadRequestException("Email is required");
        }
        emailRepository.findByOrganisationIdAndEmailIgnoreCase(organisation.getId(), email)
                .ifPresent(existing -> {
                    if (existing.getUser() != null && !existing.getUser().getId().equals(user.getId())) {
                        throw new ConflictException("Email is already registered in this organisation");
                    }
                });
        boolean isPrimary = Boolean.TRUE.equals(request.isPrimary()) || user.getEmails().isEmpty();
        if (isPrimary) {
            user.getEmails().forEach(e -> e.setPrimary(false));
        }
        user.addEmail(email, isPrimary, null);
        user.bumpDataHash();
        OrganisationUser saved = userRepository.save(user);
        audit.logPersisted(LogLevel.INFO, LogCategory.USER_MANAGEMENT, AuthAuditService.ORG_USER_UPDATED,
                identifierOf(saved), organisation.getSlug(), organisation.getId(), "added email " + email);
        return userMapper.toResponse(saved, userFieldService.readMetadata(saved.getId()));
    }

    @Transactional
    public OrganisationUserResponse deleteEmail(String platformSlug, Long organisationId, Long userId,
                                                Long emailId, OrgActor requester) {
        Organisation organisation = resolveForUser(platformSlug, organisationId, userId, requester);
        OrganisationUser user = findUser(organisation, userId);
        OrganisationUserEmail target = user.getEmails().stream()
                .filter(e -> e.getId() != null && e.getId().equals(emailId))
                .findFirst()
                .orElseThrow(() -> ResourceNotFoundException.of("Organisation user email", emailId));
        if (organisation.isEmailRequired() && user.getEmails().size() <= 1) {
            throw new BadRequestException("Cannot remove the only email address when organisation requires email");
        }
        boolean wasPrimary = target.isPrimary();
        user.removeEmail(target.getEmail());
        if (wasPrimary && !user.getEmails().isEmpty()) {
            user.getEmails().stream().findFirst().ifPresent(e -> e.setPrimary(true));
        }
        user.bumpDataHash();
        OrganisationUser saved = userRepository.save(user);
        audit.logPersisted(LogLevel.INFO, LogCategory.USER_MANAGEMENT, AuthAuditService.ORG_USER_UPDATED,
                identifierOf(saved), organisation.getSlug(), organisation.getId(), "removed email " + target.getEmail());
        return userMapper.toResponse(saved, userFieldService.readMetadata(saved.getId()));
    }

    @Transactional
    public OrganisationUserResponse setPrimaryEmail(String platformSlug, Long organisationId, Long userId,
                                                    Long emailId, OrgActor requester) {
        Organisation organisation = resolveForUser(platformSlug, organisationId, userId, requester);
        OrganisationUser user = findUser(organisation, userId);
        OrganisationUserEmail target = user.getEmails().stream()
                .filter(e -> e.getId() != null && e.getId().equals(emailId))
                .findFirst()
                .orElseThrow(() -> ResourceNotFoundException.of("Organisation user email", emailId));
        user.getEmails().forEach(e -> e.setPrimary(false));
        target.setPrimary(true);
        user.bumpDataHash();
        OrganisationUser saved = userRepository.save(user);
        audit.logPersisted(LogLevel.INFO, LogCategory.USER_MANAGEMENT, AuthAuditService.ORG_USER_UPDATED,
                identifierOf(saved), organisation.getSlug(), organisation.getId(), "set primary email " + target.getEmail());
        return userMapper.toResponse(saved, userFieldService.readMetadata(saved.getId()));
    }

    @Transactional
    public OrganisationUserResponse addPhone(String platformSlug, Long organisationId, Long userId,
                                             OrgActor requester, AddUserPhoneRequest request) {
        Organisation organisation = resolveForUser(platformSlug, organisationId, userId, requester);
        OrganisationUser user = findUser(organisation, userId);
        String phone = cleanedPhone(request.phone());
        if (phone == null) {
            throw new BadRequestException("Phone is required");
        }
        phoneRepository.findByOrganisationIdAndPhone(organisation.getId(), phone)
                .ifPresent(existing -> {
                    if (existing.getUser() != null && !existing.getUser().getId().equals(user.getId())) {
                        throw new ConflictException("Phone number is already registered in this organisation");
                    }
                });
        boolean isPrimary = Boolean.TRUE.equals(request.isPrimary()) || user.getPhones().isEmpty();
        if (isPrimary) {
            user.getPhones().forEach(p -> p.setPrimary(false));
        }
        user.addPhone(phone, isPrimary, null);
        user.bumpDataHash();
        OrganisationUser saved = userRepository.save(user);
        audit.logPersisted(LogLevel.INFO, LogCategory.USER_MANAGEMENT, AuthAuditService.ORG_USER_UPDATED,
                identifierOf(saved), organisation.getSlug(), organisation.getId(), "added phone " + phone);
        return userMapper.toResponse(saved, userFieldService.readMetadata(saved.getId()));
    }

    @Transactional
    public OrganisationUserResponse deletePhone(String platformSlug, Long organisationId, Long userId,
                                                Long phoneId, OrgActor requester) {
        Organisation organisation = resolveForUser(platformSlug, organisationId, userId, requester);
        OrganisationUser user = findUser(organisation, userId);
        OrganisationUserPhone target = user.getPhones().stream()
                .filter(p -> p.getId() != null && p.getId().equals(phoneId))
                .findFirst()
                .orElseThrow(() -> ResourceNotFoundException.of("Organisation user phone", phoneId));
        if (organisation.isPhoneRequired() && user.getPhones().size() <= 1) {
            throw new BadRequestException("Cannot remove the only phone number when organisation requires phone");
        }
        boolean wasPrimary = target.isPrimary();
        user.removePhone(target.getPhone());
        if (wasPrimary && !user.getPhones().isEmpty()) {
            user.getPhones().stream().findFirst().ifPresent(p -> p.setPrimary(true));
        }
        user.bumpDataHash();
        OrganisationUser saved = userRepository.save(user);
        audit.logPersisted(LogLevel.INFO, LogCategory.USER_MANAGEMENT, AuthAuditService.ORG_USER_UPDATED,
                identifierOf(saved), organisation.getSlug(), organisation.getId(), "removed phone " + target.getPhone());
        return userMapper.toResponse(saved, userFieldService.readMetadata(saved.getId()));
    }

    @Transactional
    public OrganisationUserResponse setPrimaryPhone(String platformSlug, Long organisationId, Long userId,
                                                    Long phoneId, OrgActor requester) {
        Organisation organisation = resolveForUser(platformSlug, organisationId, userId, requester);
        OrganisationUser user = findUser(organisation, userId);
        OrganisationUserPhone target = user.getPhones().stream()
                .filter(p -> p.getId() != null && p.getId().equals(phoneId))
                .findFirst()
                .orElseThrow(() -> ResourceNotFoundException.of("Organisation user phone", phoneId));
        user.getPhones().forEach(p -> p.setPrimary(false));
        target.setPrimary(true);
        user.bumpDataHash();
        OrganisationUser saved = userRepository.save(user);
        audit.logPersisted(LogLevel.INFO, LogCategory.USER_MANAGEMENT, AuthAuditService.ORG_USER_UPDATED,
                identifierOf(saved), organisation.getSlug(), organisation.getId(), "set primary phone " + target.getPhone());
        return userMapper.toResponse(saved, userFieldService.readMetadata(saved.getId()));
    }

    // ---------------------------------------------------------------------
    // Administrator-triggered verification
    // ---------------------------------------------------------------------

    /**
     * Sends a verification code/link to one of the user's own addresses so they
     * can prove they own it. Resolves the target from {@code emailId} /
     * {@code phoneId}, falling back to the primary address for the channel; an
     * id that is not on this user is a 404 rather than a silent fallback.
     */
    @Transactional
    public VerificationRequestResponse sendVerification(String platformSlug, Long organisationId, Long userId,
                                                        OrgActor requester, SendUserVerificationRequest request) {
        Organisation organisation = resolveForUser(platformSlug, organisationId, userId, requester);
        OrganisationUser user = findUser(organisation, userId);
        VerificationChannel channel = request.channel();
        String address = resolveAddress(user, channel, request.emailId(), request.phoneId());
        // The purpose has to follow the channel: an SMS delivery is a phone
        // verification, and the service rejects a purpose/channel mismatch.
        VerificationPurpose purpose = channel == VerificationChannel.SMS
                ? VerificationPurpose.PHONE_VERIFICATION
                : VerificationPurpose.EMAIL_VERIFICATION;

        VerificationRequestResponse response = verificationService.requestForUser(
                organisation, user, purpose, channel, address, request.delivery());
        audit.logPersisted(LogLevel.INFO, LogCategory.USER_MANAGEMENT, AuthAuditService.ORG_USER_VERIFICATION_SENT,
                identifierOf(user), organisation.getSlug(), organisation.getId(),
                channel.name() + " " + address);
        return response;
    }

    /**
     * Sends a password reset so the user chooses their own password. The
     * organisation must have password resets enabled; the code is bound to this
     * user, so it cannot be completed by whoever intercepted the mail.
     */
    @Transactional
    public VerificationRequestResponse sendPasswordReset(String platformSlug, Long organisationId, Long userId,
                                                        OrgActor requester, SendPasswordResetRequest request) {
        Organisation organisation = resolveForUser(platformSlug, organisationId, userId, requester);
        OrganisationUser user = findUser(organisation, userId);
        if (!user.isEnabled()) {
            throw new BadRequestException("Cannot send a password reset to a disabled account");
        }
        VerificationChannel channel = request.emailId() != null || request.phoneId() == null
                ? VerificationChannel.EMAIL
                : VerificationChannel.SMS;
        String address = resolveAddress(user, channel, request.emailId(), request.phoneId());

        VerificationRequestResponse response = verificationService.requestForUser(
                organisation, user, VerificationPurpose.PASSWORD_RESET, channel, address, request.delivery());
        audit.logPersisted(LogLevel.INFO, LogCategory.USER_MANAGEMENT, AuthAuditService.ORG_USER_PASSWORD_RESET_SENT,
                identifierOf(user), organisation.getSlug(), organisation.getId(),
                channel.name() + " " + address);
        return response;
    }

    /**
     * Overrides an address's verified state without proof of ownership. Used by
     * support to unblock a user whose delivery failed; it is a SECURITY-level
     * audit event because it lets an administrator vouch for an address the user
     * has not proven.
     */
    @Transactional
    public OrganisationUserResponse setAddressVerified(String platformSlug, Long organisationId, Long userId,
                                                      Long addressId, boolean email, boolean verified,
                                                      OrgActor requester) {
        Organisation organisation = resolveForUser(platformSlug, organisationId, userId, requester);
        OrganisationUser user = findUser(organisation, userId);
        String address;
        if (email) {
            OrganisationUserEmail target = user.getEmails().stream()
                    .filter(e -> e.getId() != null && e.getId().equals(addressId))
                    .findFirst()
                    .orElseThrow(() -> ResourceNotFoundException.of("Organisation user email", addressId));
            target.setVerifiedAt(verified ? java.time.Instant.now() : null);
            address = target.getEmail();
        } else {
            OrganisationUserPhone target = user.getPhones().stream()
                    .filter(p -> p.getId() != null && p.getId().equals(addressId))
                    .findFirst()
                    .orElseThrow(() -> ResourceNotFoundException.of("Organisation user phone", addressId));
            target.setVerifiedAt(verified ? java.time.Instant.now() : null);
            address = target.getPhone();
        }
        // A forced "verify at next login" is satisfied once the address is proven,
        // so clear it rather than challenging the user for a no-op.
        if (verified) {
            if (email) {
                user.setRequireEmailVerificationAtNextLogin(false);
            } else {
                user.setRequirePhoneVerificationAtNextLogin(false);
            }
        }
        user.bumpDataHash();
        OrganisationUser saved = userRepository.save(user);
        audit.logPersisted(verified ? LogLevel.WARN : LogLevel.INFO, LogCategory.SECURITY,
                verified ? AuthAuditService.ORG_USER_ADDRESS_VERIFIED : AuthAuditService.ORG_USER_ADDRESS_UNVERIFIED,
                identifierOf(saved), organisation.getSlug(), organisation.getId(),
                (email ? "email " : "phone ") + address + " manual override");
        return userMapper.toResponse(saved, userFieldService.readMetadata(saved.getId()));
    }

    /**
     * The address to deliver to: the requested one when it belongs to the user,
     * otherwise their primary address for the channel. A supplied id that is not
     * on this user is rejected so a caller cannot think they targeted one address
     * while another receives the code.
     */
    private String resolveAddress(OrganisationUser user, VerificationChannel channel,
                                  Long emailId, Long phoneId) {
        if (emailId != null) {
            return user.getEmails().stream()
                    .filter(e -> e.getId() != null && e.getId().equals(emailId))
                    .findFirst()
                    .orElseThrow(() -> ResourceNotFoundException.of("Organisation user email", emailId))
                    .getEmail();
        }
        if (phoneId != null) {
            return user.getPhones().stream()
                    .filter(p -> p.getId() != null && p.getId().equals(phoneId))
                    .findFirst()
                    .orElseThrow(() -> ResourceNotFoundException.of("Organisation user phone", phoneId))
                    .getPhone();
        }
        String primary = channel == VerificationChannel.SMS ? user.getPrimaryPhone() : user.getPrimaryEmail();
        if (primary == null || primary.isBlank()) {
            throw new BadRequestException("The user has no " + channel.name().toLowerCase()
                    + " address to send to — add one first");
        }
        return primary;
    }

    private Organisation resolveForUser(String platformSlug, Long organisationId, Long userId, OrgActor requester) {
        if (!requester.isPlatformUser() && requester instanceof OrgUserPrincipal principal && principal.id().equals(userId)) {
            Platform platform = platformAccess.findPlatform(platformSlug);
            Organisation organisation = organisationAccess.findOrganisationById(organisationId);
            organisationAccess.requireOrgUserOf(organisation, requester);
            return organisation;
        }
        return resolve(platformSlug, organisationId, requester, true, Permission.ORGANISATION_USER_UPDATE);
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
        return user.getUsername() != null ? user.getUsername()
                : user.getPrimaryEmail() != null ? user.getPrimaryEmail()
                : user.getPrimaryPhone() != null ? user.getPrimaryPhone() : "unknown";
    }

    private void assertIdentifiersFree(Organisation organisation, String email, String username,
                                       String phone, OrganisationUser exclude) {
        if (email != null) {
            Optional<OrganisationUser> existing = userRepository.findByOrganisationIdAndEmail(organisation.getId(), email);
            if (existing.isPresent() && (exclude == null || !existing.get().getId().equals(exclude.getId()))) {
                throw new ConflictException("An organisation user with email " + email
                        + " already exists in this organisation");
            }
        }
        if (username != null) {
            Optional<OrganisationUser> existing = userRepository.findByOrganisationIdAndUsername(organisation.getId(), username);
            if (existing.isPresent() && (exclude == null || !existing.get().getId().equals(exclude.getId()))) {
                throw new ConflictException("An organisation user with username " + username
                        + " already exists in this organisation");
            }
        }
        if (phone != null) {
            Optional<OrganisationUser> existing = userRepository.findByOrganisationIdAndPhone(organisation.getId(), phone);
            if (existing.isPresent() && (exclude == null || !existing.get().getId().equals(exclude.getId()))) {
                throw new ConflictException("An organisation user with phone " + phone
                        + " already exists in this organisation");
            }
        }
    }

    private Set<OrganisationRole> resolveRoles(Organisation organisation, Set<Long> roleIds) {
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

    private String cleanedName(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizedEmail(String email) {
        if (email == null) return null;
        String normalized = Emails.normalize(email);
        return normalized.isBlank() ? null : normalized;
    }

    private String cleanedUsername(String value) {
        if (value == null) return null;
        String normalized = Usernames.normalize(value);
        return normalized.isEmpty() ? null : normalized;
    }

    private String cleanedPhone(String value) {
        if (value == null) return null;
        String normalized = Phones.normalize(value);
        return normalized.isEmpty() ? null : normalized;
    }
}
