package com.nexxserve.nexxauth.service;

import com.nexxserve.nexxauth.dto.request.ChallengeVerifyRequest;
import com.nexxserve.nexxauth.dto.request.OrgLoginRequest;
import com.nexxserve.nexxauth.dto.request.OrgRegisterRequest;
import com.nexxserve.nexxauth.dto.response.LoginChallenge;
import com.nexxserve.nexxauth.dto.response.OrgAuthResponse;
import com.nexxserve.nexxauth.entity.AuthType;
import com.nexxserve.nexxauth.entity.LogCategory;
import com.nexxserve.nexxauth.entity.LogLevel;
import com.nexxserve.nexxauth.entity.OrgIdentifierType;
import com.nexxserve.nexxauth.entity.Organisation;
import com.nexxserve.nexxauth.entity.OrganisationAuthConfig;
import com.nexxserve.nexxauth.entity.OrganisationClient;
import com.nexxserve.nexxauth.entity.OrganisationRole;
import com.nexxserve.nexxauth.entity.OrganisationSigningKey;
import com.nexxserve.nexxauth.entity.OrganisationUser;
import com.nexxserve.nexxauth.entity.OrgUserAction;
import com.nexxserve.nexxauth.entity.Platform;
import com.nexxserve.nexxauth.entity.VerificationChannel;
import com.nexxserve.nexxauth.entity.VerificationPurpose;
import com.nexxserve.nexxauth.exception.BadRequestException;
import com.nexxserve.nexxauth.exception.ConflictException;
import com.nexxserve.nexxauth.exception.InvalidCredentialsException;
import com.nexxserve.nexxauth.exception.PasswordExpiredException;
import com.nexxserve.nexxauth.exception.RefreshTokenException;
import com.nexxserve.nexxauth.exception.ResourceNotFoundException;
import com.nexxserve.nexxauth.mapper.OrganisationClientMapper;
import com.nexxserve.nexxauth.mapper.OrganisationUserMapper;
import com.nexxserve.nexxauth.repository.OrganisationClientRepository;
import com.nexxserve.nexxauth.repository.OrganisationRepository;
import com.nexxserve.nexxauth.repository.OrganisationRoleRepository;
import com.nexxserve.nexxauth.repository.OrganisationUserRepository;
import com.nexxserve.nexxauth.security.AuthTiming;
import com.nexxserve.nexxauth.security.OrgJwtService;
import com.nexxserve.nexxauth.util.Emails;
import com.nexxserve.nexxauth.util.Phones;
import com.nexxserve.nexxauth.util.Usernames;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class OrganisationAuthService {

    private final PlatformAccess platformAccess;
    private final OrganisationClientRepository clientRepository;
    private final OrganisationRepository organisationRepository;
    private final OrganisationRoleRepository roleRepository;
    private final OrganisationUserRepository userRepository;
    private final OrganisationRefreshTokenService refreshTokenService;
    private final OrgJwtService orgJwtService;
    private final OrgKeyService orgKeyService;
    private final PasswordEncoder passwordEncoder;
    private final OrganisationUserMapper userMapper;
    private final OrganisationClientMapper clientMapper;
    private final AuthAuditService audit;
    private final OrganisationAuthConfigService authConfigService;
    private final OrganisationSessionSettingsService sessionSettingsService;
    private final AuthTiming authTiming;
    private final OrganisationUserFieldService userFieldService;
    private final OrgUserActions orgUserActions;
    private final OrganisationSessionService sessionService;
    private final com.nexxserve.nexxauth.security.AccountLockoutService accountLockout;
    private final com.nexxserve.nexxauth.repository.OrganisationVerificationTokenRepository verificationTokenRepository;
    private final VerificationProperties verificationProperties;
    private final OrganisationVerificationService verificationService;

    public OrganisationAuthService(PlatformAccess platformAccess,
                                   OrganisationClientRepository clientRepository,
                                   OrganisationRepository organisationRepository,
                                   OrganisationRoleRepository roleRepository,
                                   OrganisationUserRepository userRepository,
                                   OrganisationRefreshTokenService refreshTokenService, OrgJwtService orgJwtService,
                                   OrgKeyService orgKeyService,
                                   PasswordEncoder passwordEncoder, OrganisationUserMapper userMapper,
                                   OrganisationClientMapper clientMapper,
                                   AuthAuditService audit, OrganisationAuthConfigService authConfigService,
                                   OrganisationSessionSettingsService sessionSettingsService,
                                   AuthTiming authTiming, OrganisationUserFieldService userFieldService,
                                   OrgUserActions orgUserActions, OrganisationSessionService sessionService,
                                   com.nexxserve.nexxauth.security.AccountLockoutService accountLockout,
                                   com.nexxserve.nexxauth.repository.OrganisationVerificationTokenRepository verificationTokenRepository,
                                   VerificationProperties verificationProperties,
                                   OrganisationVerificationService verificationService) {
        this.platformAccess = platformAccess;
        this.clientRepository = clientRepository;
        this.organisationRepository = organisationRepository;
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.refreshTokenService = refreshTokenService;
        this.orgJwtService = orgJwtService;
        this.orgKeyService = orgKeyService;
        this.passwordEncoder = passwordEncoder;        this.userMapper = userMapper;
        this.clientMapper = clientMapper;

        this.audit = audit;
        this.authConfigService = authConfigService;
        this.sessionSettingsService = sessionSettingsService;
        this.authTiming = authTiming;
        this.userFieldService = userFieldService;
        this.orgUserActions = orgUserActions;
        this.sessionService = sessionService;
        this.accountLockout = accountLockout;
        this.verificationTokenRepository = verificationTokenRepository;
        this.verificationProperties = verificationProperties;
        this.verificationService = verificationService;
    }

    @Transactional
    public OrgAuthResponse register(String platformSlug, OrgRegisterRequest request, String clientId,
                                     String ipAddress, String userAgent) {
        return register(platformSlug, request, clientId, ipAddress, userAgent, null);
    }

    @Transactional
    public OrgAuthResponse register(String platformSlug, OrgRegisterRequest request, String clientId,
                                     String ipAddress, String userAgent, String hostname) {
        OrganisationClient client = resolveClient(clientId);
        enforceClientRestrictions(client, "register");
        Organisation organisation = resolveOrganisation(platformSlug, client, null);
        String email = normalizedEmail(request.email());
        String username = cleanedUsername(request.username());
        String phone = cleanedPhone(request.phone());

        if (organisation.isEmailRequired() && email == null)
            throw new ConflictException("A valid email is required for this organisation");
        if (organisation.isUsernameRequired() && username == null)
            throw new ConflictException("A username is required to register for this organisation");
        if (organisation.isPhoneRequired() && phone == null)
            throw new BadRequestException("A phone number is required for this organisation");
        if (!hasLoginCapableIdentifier(organisation, email, username, phone))
            throw new BadRequestException("At least one login-enabled identifier (email, username or phone) is required");
        assertIdentifiersFree(organisation, email, username, phone);

        OrganisationUser user = new OrganisationUser();
        user.setOrganisation(organisation);
        user.setFirstName(request.firstName());
        user.setLastName(cleanedName(request.lastName()));
        user.setEmail(email);
        user.setUsername(username);
        user.setPhone(phone);
        if (request.password() != null && !request.password().isBlank()) {
            authConfigService.setPassword(user, request.password());
        } else if (authConfigService.configOf(organisation).isPasswordEnabled()) {
            throw new BadRequestException("Password is required for the PASSWORD auth method");
        }
        OrganisationUser saved = userRepository.save(user);
        applyRegisterMetadata(request, saved);
        assignDefaultRoles(organisation, saved);
        // Re-fetch with roles loaded for the role restriction check
        OrganisationUser userWithRoles = userRepository.findById(saved.getId()).orElse(saved);
        enforceRoleRestrictions(client, userWithRoles);
        // When the org requires verification on registration, kick the flow
        // off immediately: a code/link (org's verification mode) is sent to
        // the unverified identifier, and the session below is gated until it
        // is verified.
        verificationService.sendOnRegisterVerification(organisation, userWithRoles);
        audit.logPersisted(LogLevel.INFO, LogCategory.AUTH, AuthAuditService.ORG_REGISTER, identifierOf(saved),
                organisation.getSlug(), organisation.getId(), null);
        return issueTokens(saved, client, ipAddress, userAgent, hostname);
    }

    @Transactional
    public OrgAuthResponse login(String platformSlug, OrgLoginRequest request, String clientId,
                                  String ipAddress, String userAgent) {
        return login(platformSlug, request, clientId, ipAddress, userAgent, null);
    }

    @Transactional
    public OrgAuthResponse login(String platformSlug, OrgLoginRequest request, String clientId,
                                  String ipAddress, String userAgent, String hostname) {
        OrganisationClient client = resolveClient(clientId);
        enforceClientRestrictions(client, "login");
        Organisation organisation = resolveOrganisation(platformSlug, client, request.organisationId());
        AuthType method = request.authType() != null ? request.authType() : AuthType.PASSWORD;
        return switch (method) {
            case PASSWORD -> passwordLogin(organisation, request, client, ipAddress, userAgent, hostname);
            case OTP -> otpLogin(organisation, request, client, ipAddress, userAgent, hostname);
        };
    }

    /** Completes a login that was paused by a server-sent challenge (2FA or
     * verify-at-next-login). The challenge token + code resolve to the user;
     * once the obligation is cleared, any remaining obligation is started,
     * otherwise a full session is issued. */
    @Transactional
    public OrgAuthResponse verifyLoginChallenge(String platformSlug, ChallengeVerifyRequest request,
                                                String clientId, String ipAddress, String userAgent, String hostname) {
        var resolved = verificationService.resolveLoginChallenge(request.challengeToken(), request.code());
        OrganisationUser user = resolved.user();
        Organisation organisation = user.getOrganisation();
        if (!organisation.getPlatform().getSlug().equals(platformSlug)) {
            throw ResourceNotFoundException.of("Organisation", organisation.getId());
        }
        OrganisationClient client = resolveClient(clientId);
        if (client != null && !organisation.getId().equals(client.getOrganisation().getId())) {
            throw new BadRequestException("Client does not belong to the organisation of the challenge");
        }
        enforceRoleRestrictions(client, user);

        if (resolved.purpose() == VerificationPurpose.EMAIL_VERIFICATION
                || resolved.purpose() == VerificationPurpose.PHONE_VERIFICATION) {
            verificationService.completeChallengeVerification(user, resolved.purpose(), Instant.now());
            var pending = firstPendingLoginObligation(authConfigService.configOf(organisation), user);
            if (pending != null) {
                LoginChallenge challenge = verificationService.startLoginChallenge(
                        organisation, user, pending.purpose(), pending.channel());
                return challengeResponse(user, challenge);
            }
        }
        audit.logPersisted(LogLevel.INFO, LogCategory.AUTH, AuthAuditService.ORG_LOGIN_SUCCESS,
                resolved.identifier(), organisation.getSlug(), organisation.getId(),
                "challenge " + resolved.purpose().name());
        return issueTokens(user, client, ipAddress, userAgent, hostname);
    }

    /** First unresolved login obligation of {@code user}, or null when tokens
     * can be issued straight away. Address-verification flags take precedence
     * over 2FA so a code an admin forced is consumed before the standing
     * multi-factor rule. */
    private LoginObligation firstPendingLoginObligation(OrganisationAuthConfig config, OrganisationUser user) {
        if (user.isRequireEmailVerificationAtNextLogin()
                && user.getEmail() != null && user.getEmailVerifiedAt() == null) {
            return new LoginObligation(VerificationPurpose.EMAIL_VERIFICATION, VerificationChannel.EMAIL);
        }
        if (user.isRequirePhoneVerificationAtNextLogin()
                && user.getPhone() != null && user.getPhoneVerifiedAt() == null) {
            return new LoginObligation(VerificationPurpose.PHONE_VERIFICATION, VerificationChannel.SMS);
        }
        if (config.isTwoFactorEnabled()) {
            VerificationChannel channel = user.getEmail() != null ? VerificationChannel.EMAIL
                    : user.getPhone() != null ? VerificationChannel.SMS : null;
            if (channel == null) {
                throw new BadRequestException("Two-factor authentication requires a verified email or phone number");
            }
            return new LoginObligation(VerificationPurpose.TWO_FACTOR, channel);
        }
        return null;
    }

    private OrgAuthResponse challengeResponse(OrganisationUser user, LoginChallenge challenge) {
        return OrgAuthResponse.challenge(challenge,
                userMapper.toResponse(user, userFieldService.readMetadata(user.getId())),
                orgUserActions.of(user));
    }

    record LoginObligation(VerificationPurpose purpose, VerificationChannel channel) {
    }

    /** OTP login: the user was sent a one-time code (LOGIN_OTP) and presents it
     * here. The code is matched against the stored, hashed value for the
     * identifier; success issues the same session as password login. */
    private OrgAuthResponse otpLogin(Organisation organisation, OrgLoginRequest request,
                                     OrganisationClient client, String ipAddress, String userAgent, String hostname) {
        if (request.otpCode() == null || request.otpCode().isBlank()) {
            throw new BadRequestException("A one-time code is required for the OTP auth method");
        }
        if (!authConfigService.configOf(organisation).isOtpLoginEnabled()) {
            throw new BadRequestException("OTP login is not enabled for this organisation");
        }
        String identifier = request.identifier().trim();
        String code = request.otpCode().trim();

        // The code was sent to the identifier on a specific channel; derive it
        // from the declared identifier type so the storage lookup is unambiguous.
        com.nexxserve.nexxauth.entity.VerificationChannel channel = channelForIdentifier(request.identifierType());
        String normalized = normalizedIdentifier(channel, identifier);
        var token = verificationTokenRepository.findActive(organisation.getId(),
                        com.nexxserve.nexxauth.entity.VerificationPurpose.LOGIN_OTP,
                        channel, normalized, java.time.Instant.now())
                .orElseThrow(() -> {
                    audit.logPersisted(LogLevel.WARN, LogCategory.SECURITY, AuthAuditService.ORG_LOGIN_FAILURE,
                            identifier, organisation.getSlug(), organisation.getId(), "otp_no_token");
                    accountLockout.recordFailure(organisation.getId(), identifier);
                    return new InvalidCredentialsException();
                });

        if (!java.security.MessageDigest.isEqual(
                hashHex(code).getBytes(java.nio.charset.StandardCharsets.UTF_8),
                token.getTokenHash().getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            audit.logPersisted(LogLevel.WARN, LogCategory.SECURITY, AuthAuditService.ORG_LOGIN_FAILURE,
                    identifier, organisation.getSlug(), organisation.getId(), "otp_invalid");
            accountLockout.recordFailure(organisation.getId(), identifier);
            token.setAttempts(token.getAttempts() + 1);
            if (token.getAttempts() >= verificationProperties.getMaxAttempts()) {
                token.setConsumedAt(java.time.Instant.now());
            }
            throw new InvalidCredentialsException();
        }
        if (accountLockout.isLocked(organisation.getId(), identifier)) {
            throw new InvalidCredentialsException();
        }

        token.setConsumedAt(java.time.Instant.now());
        // The account was already resolved when the code was issued, so the
        // token carries it. Fall back to a fresh lookup (identifier rules do
        // not apply: the user proved ownership of the address by receiving it).
        OrganisationUser user = token.getOrganisationUser();
        if (user == null) {
            user = findByIdentifier(organisation, identifier, null)
                    .or(() -> userFieldService.findUserByLoginField(organisation, identifier))
                    .orElseThrow(() -> {
                        audit.logPersisted(LogLevel.WARN, LogCategory.SECURITY, AuthAuditService.ORG_LOGIN_FAILURE,
                                identifier, organisation.getSlug(), organisation.getId(), "otp_no_account");
                        return new InvalidCredentialsException();
                    });
        }
        if (!user.isEnabled()) {
            audit.logPersisted(LogLevel.WARN, LogCategory.SECURITY, AuthAuditService.ORG_LOGIN_FAILURE,
                    identifier, organisation.getSlug(), organisation.getId(), "disabled");
            throw new InvalidCredentialsException();
        }
        accountLockout.clearFailures(organisation.getId(), identifier);
        audit.logPersisted(LogLevel.INFO, LogCategory.AUTH, AuthAuditService.ORG_LOGIN_SUCCESS, identifier,
                organisation.getSlug(), organisation.getId(), "otp");
        enforceRoleRestrictions(client, user);
        return issueTokens(user, client, ipAddress, userAgent, hostname);
    }

    private com.nexxserve.nexxauth.entity.VerificationChannel channelForIdentifier(
            com.nexxserve.nexxauth.entity.OrgIdentifierType identifierType) {
        if (identifierType == com.nexxserve.nexxauth.entity.OrgIdentifierType.PHONE) {
            return com.nexxserve.nexxauth.entity.VerificationChannel.SMS;
        }
        // EMAIL, USERNAME and auto-detection all route through the email channel:
        // a login code travels to the address the user asked to have it sent to.
        return com.nexxserve.nexxauth.entity.VerificationChannel.EMAIL;
    }

    private String normalizedIdentifier(com.nexxserve.nexxauth.entity.VerificationChannel channel, String identifier) {
        return switch (channel) {
            case EMAIL -> com.nexxserve.nexxauth.util.Emails.normalize(identifier);
            case SMS -> com.nexxserve.nexxauth.util.Phones.normalize(identifier);
        };
    }

    private String hashHex(String raw) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            return java.util.Base64.getEncoder().encodeToString(
                    digest.digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private OrgAuthResponse passwordLogin(Organisation organisation, OrgLoginRequest request,
                                          OrganisationClient client, String ipAddress, String userAgent, String hostname) {
        if (request.password() == null || request.password().isBlank())
            throw new BadRequestException("Password is required for the PASSWORD auth method");
        String identifier = request.identifier().trim();

        // Per-account lockout: check before doing any credential work.
        if (accountLockout.isLocked(organisation.getId(), identifier)) {
            authTiming.equalsUnknown(request.password()); // timing burn
            throw new InvalidCredentialsException();
        }

        if (!authConfigService.configOf(organisation).isPasswordEnabled()) {
            accountLockout.recordFailure(organisation.getId(), identifier);
            audit.logPersisted(LogLevel.WARN, LogCategory.SECURITY, AuthAuditService.ORG_LOGIN_FAILURE, identifier,
                    organisation.getSlug(), organisation.getId(), null);
            authTiming.equalsUnknown(request.password());
            throw new InvalidCredentialsException();
        }
        OrganisationUser user = findByIdentifier(organisation, identifier, request.identifierType())
                .or(() -> userFieldService.findUserByLoginField(organisation, identifier))
                .orElseThrow(() -> {
                    audit.logPersisted(LogLevel.WARN, LogCategory.SECURITY, AuthAuditService.ORG_LOGIN_FAILURE, identifier,
                            organisation.getSlug(), organisation.getId(), null);
                    accountLockout.recordFailure(organisation.getId(), identifier);
                    authTiming.equalsUnknown(request.password());
                    return new InvalidCredentialsException();
                });
        String hash = user.getPasswordHash();
        boolean passwordMatches = hash != null
                ? passwordEncoder.matches(request.password(), hash)
                : authTiming.equalsUnknown(request.password());
        if (user.getAuthType() != AuthType.PASSWORD || !user.isEnabled() || !passwordMatches) {
            audit.logPersisted(LogLevel.WARN, LogCategory.SECURITY, AuthAuditService.ORG_LOGIN_FAILURE, identifier,
                    organisation.getSlug(), organisation.getId(), null);
            accountLockout.recordFailure(organisation.getId(), identifier);
            throw new InvalidCredentialsException();
        }
        var config = authConfigService.configOf(organisation);
        if (authConfigService.isPasswordExpired(config, user)) {
            audit.logPersisted(LogLevel.WARN, LogCategory.SECURITY, AuthAuditService.ORG_LOGIN_FAILURE, identifier,
                    organisation.getSlug(), organisation.getId(), "password_expired");
            throw new PasswordExpiredException();
        }
        accountLockout.clearFailures(organisation.getId(), identifier);
        audit.logPersisted(LogLevel.INFO, LogCategory.AUTH, AuthAuditService.ORG_LOGIN_SUCCESS, identifier,
                organisation.getSlug(), organisation.getId(), null);
        enforceRoleRestrictions(client, user);
        // A login may not hand out tokens immediately: 2FA (every login) and
        // an admin-set "verify at next login" force a server-sent challenge
        // that must be solved on the challenge endpoint first.
        var pending = firstPendingLoginObligation(authConfigService.configOf(organisation), user);
        if (pending != null) {
            LoginChallenge challenge = verificationService.startLoginChallenge(
                    organisation, user, pending.purpose(), pending.channel());
            return challengeResponse(user, challenge);
        }
        return issueTokens(user, client, ipAddress, userAgent, hostname);
    }

    @Transactional
    public OrgAuthResponse refresh(String platformSlug, String rawRefreshToken,
                                    String ipAddress, String userAgent) {
        return refresh(platformSlug, rawRefreshToken, ipAddress, userAgent, null);
    }

    @Transactional
    public OrgAuthResponse refresh(String platformSlug, String rawRefreshToken,
                                    String ipAddress, String userAgent, String hostname) {
        OrganisationUser resolved = refreshTokenService.resolveSubject(rawRefreshToken);
        if (orgUserActions.hasPendingGatingAction(resolved))
            throw new RefreshTokenException("Pending action required");
        OrganisationClient client = refreshTokenService.clientOf(rawRefreshToken);
        // Carry forward the session id from the old token
        String existingSessionId = refreshTokenService.sessionIdOf(rawRefreshToken);
        var rotation = refreshTokenService.rotateWithSubject(rawRefreshToken,
                subject -> sessionSettingsService.refreshTokenTtl(subject.getOrganisation(), client));
        audit.logPersisted(LogLevel.INFO, LogCategory.AUTH, AuthAuditService.ORG_REFRESH,
                identifierOf(rotation.subject()),
                rotation.subject().getOrganisation().getSlug(),
                rotation.subject().getOrganisation().getId(), null);
        return issueTokens(rotation.subject(), rotation.newToken(), client,
                ipAddress, userAgent, existingSessionId, hostname);
    }

    @Transactional
    public void logout(String platformSlug, String rawRefreshToken) {
        refreshTokenService.findSubjectForAudit(rawRefreshToken).ifPresent(user ->
                audit.logPersisted(LogLevel.INFO, LogCategory.AUTH, AuthAuditService.ORG_LOGOUT,
                        identifierOf(user),
                        user.getOrganisation().getSlug(),
                        user.getOrganisation().getId(), null));
        refreshTokenService.revoke(rawRefreshToken);
    }

    private void assignDefaultRoles(Organisation organisation, OrganisationUser user) {
        List<OrganisationRole> defaults = roleRepository.findByOrganisationIdAndDefaultRoleTrue(organisation.getId());
        if (!defaults.isEmpty()) user.setRoles(new java.util.HashSet<>(defaults));
    }

    private String identifierOf(OrganisationUser user) {
        return user.getUsername() != null ? user.getUsername()
                : user.getEmail() != null ? user.getEmail()
                : user.getPhone() != null ? user.getPhone() : "unknown";
    }

    private void assertIdentifiersFree(Organisation organisation, String email, String username, String phone) {
        if (email != null && userRepository.existsByOrganisationIdAndEmail(organisation.getId(), email))
            throw new ConflictException("An organisation user with email " + email + " already exists in this organisation");
        if (username != null && userRepository.existsByOrganisationIdAndUsername(organisation.getId(), username))
            throw new ConflictException("An organisation user with username " + username + " already exists in this organisation");
        if (phone != null && userRepository.existsByOrganisationIdAndPhone(organisation.getId(), phone))
            throw new ConflictException("An organisation user with phone " + phone + " already exists in this organisation");
    }

    private String cleanedName(String v) { if (v == null) return null; String t = v.trim(); return t.isEmpty() ? null : t; }
    private String normalizedEmail(String e) { if (e == null) return null; String n = Emails.normalize(e); return n.isBlank() ? null : n; }
    private String cleanedUsername(String v) { if (v == null) return null; String n = Usernames.normalize(v); return n.isEmpty() ? null : n; }
    private String cleanedPhone(String v) { if (v == null) return null; String n = Phones.normalize(v); return n.isEmpty() ? null : n; }

    private boolean hasLoginCapableIdentifier(Organisation o, String e, String u, String p) {
        return (o.isEmailCanLogin() && e != null) || (o.isUsernameCanLogin() && u != null) || (o.isPhoneCanLogin() && p != null);
    }

    private java.util.Optional<OrganisationUser> findByIdentifier(Organisation o, String id, OrgIdentifierType t) {
        if (t != null) return switch (t) {
            case EMAIL -> o.isEmailCanLogin() ? userRepository.findWithRolesByOrganisationIdAndEmail(o.getId(), Emails.normalize(id)) : java.util.Optional.empty();
            case USERNAME -> o.isUsernameCanLogin() ? userRepository.findWithRolesByOrganisationIdAndUsername(o.getId(), Usernames.normalize(id)) : java.util.Optional.empty();
            case PHONE -> o.isPhoneCanLogin() ? userRepository.findWithRolesByOrganisationIdAndPhone(o.getId(), Phones.normalize(id)) : java.util.Optional.empty();
        };
        java.util.Optional<OrganisationUser> direct = enabledIdentifierLookup(o, id);
        return direct.isPresent() ? direct : userFieldService.findUserByLoginField(o, id);
    }

    private java.util.Optional<OrganisationUser> enabledIdentifierLookup(Organisation o, String id) {
        if (o.isUsernameCanLogin()) { var u = userRepository.findWithRolesByOrganisationIdAndUsername(o.getId(), Usernames.normalize(id)); if (u.isPresent()) return u; }
        if (o.isEmailCanLogin()) { var u = userRepository.findWithRolesByOrganisationIdAndEmail(o.getId(), Emails.normalize(id)); if (u.isPresent()) return u; }
        if (o.isPhoneCanLogin()) { var u = userRepository.findWithRolesByOrganisationIdAndPhone(o.getId(), Phones.normalize(id)); if (u.isPresent()) return u; }
        return java.util.Optional.empty();
    }

    /**
     * Enforce per-client login/register restrictions. Called before the
     * organisation is resolved so we fail fast with a clear message.
     */
    private void enforceClientRestrictions(OrganisationClient client, String action) {
        if (client == null) return;
        if ("register".equals(action) && !client.isAllowRegister()) {
            audit.logPersisted(LogLevel.WARN, LogCategory.SECURITY, AuthAuditService.ORG_REGISTER_FAILURE,
                    null, client.getOrganisation().getSlug(), client.getOrganisation().getId(),
                    "client_blocked_register");
            throw new BadRequestException("Registration is not allowed from this client");
        }
        if ("login".equals(action) && !client.isAllowLogin()) {
            audit.logPersisted(LogLevel.WARN, LogCategory.SECURITY, AuthAuditService.ORG_LOGIN_FAILURE,
                    null, client.getOrganisation().getSlug(), client.getOrganisation().getId(),
                    "client_blocked_login");
            throw new BadRequestException("Login is not allowed from this client");
        }
    }

    /**
     * Enforce role-based restrictions. After the user is known, check
     * whether the client restricts which roles may login/register.
     * <p>
     * Three modes ({@link com.nexxserve.nexxauth.entity.RoleRestrictionMode}):
     * <ul>
     *   <li>{@code NONE} — no restriction; all roles are allowed.</li>
     *   <li>{@code ALLOWLIST} — only users holding at least one of the listed
     *       roles may authenticate.</li>
     *   <li>{@code BLOCKLIST} — users holding any of the listed roles are
     *       rejected; all other roles may authenticate.</li>
     * </ul>
     */
    private void enforceRoleRestrictions(OrganisationClient client, OrganisationUser user) {
        if (client == null) return;
        com.nexxserve.nexxauth.entity.RoleRestrictionMode mode = client.getRoleRestrictionMode();
        if (mode == null || mode == com.nexxserve.nexxauth.entity.RoleRestrictionMode.NONE) return;
        Set<String> restricted = clientMapper.splitRoles(client.getAllowedRoles());
        if (restricted.isEmpty()) return;

        boolean userHasRestrictedRole = user.getRoles().stream()
                .anyMatch(role -> restricted.contains(role.getName()));

        boolean blocked = switch (mode) {
            case ALLOWLIST -> !userHasRestrictedRole;  // user lacks any allowed role
            case BLOCKLIST -> userHasRestrictedRole;    // user holds a blocked role
            case NONE -> false;                         // already returned above
        };

        if (blocked) {
            Organisation organisation = user.getOrganisation();
            audit.logPersisted(LogLevel.WARN, LogCategory.SECURITY, AuthAuditService.ORG_LOGIN_FAILURE,
                    identifierOf(user), organisation.getSlug(), organisation.getId(),
                    "role_not_allowed_" + mode.name().toLowerCase());
            throw new BadRequestException("Your role is not permitted to authenticate from this client");
        }
    }

    private OrganisationClient resolveClient(String clientId) {
        if (clientId == null || clientId.isBlank()) return null;
        return clientRepository.findByClientKey(clientId.trim()).orElse(null);
    }

    private Organisation resolveOrganisation(String platformSlug, OrganisationClient client, Long organisationId) {
        if (client != null) {
            Organisation o = client.getOrganisation();
            if (!o.getPlatform().getSlug().equals(platformSlug)) throw ResourceNotFoundException.of("Organisation", o.getId());
            return o;
        }
        // Fallback: portal flow — resolve from organisationId in the body
        if (organisationId != null) {
            Organisation o = organisationRepository.findById(organisationId)
                    .orElseThrow(() -> ResourceNotFoundException.of("Organisation", organisationId));
            if (!o.getPlatform().getSlug().equals(platformSlug)) throw ResourceNotFoundException.of("Organisation", organisationId);
            return o;
        }
        throw new BadRequestException("X-Client-Id header or organisationId is required");
    }



    private OrgAuthResponse issueTokens(OrganisationUser user, OrganisationClient client,
                                         String ipAddress, String userAgent) {
        return issueTokens(user, client, ipAddress, userAgent, null);
    }

    private OrgAuthResponse issueTokens(OrganisationUser user, OrganisationClient client,
                                         String ipAddress, String userAgent, String hostname) {
        Organisation organisation = user.getOrganisation();
        if (orgUserActions.hasPendingGatingAction(user)) return issueTokens(user, null, OrgUserActions.GATING_ACCESS_TTL, null, null, null, hostname);
        refreshTokenService.enforceSessionLimit(organisation, user.getId(), client, user);
        // Dedup: if the user is logging in from the same IP+UA, reuse the existing session id
        String sessionId = sessionService.findExistingSessionId(user.getId(), ipAddress, userAgent);
        if (sessionId == null) {
            sessionId = sessionService.newSessionId();
        }
        return issueTokens(user, refreshTokenService.issueWithClient(user,
                sessionSettingsService.refreshTokenTtl(organisation, client),
                client != null ? client.getClientKey() : null,
                ipAddress, userAgent, sessionId, hostname), client, ipAddress, userAgent, sessionId);
    }

    private OrgAuthResponse issueTokens(OrganisationUser user, String refreshToken,
                                         OrganisationClient client, String ipAddress,
                                         String userAgent, String sessionId) {
        return issueTokens(user, refreshToken, client, ipAddress, userAgent, sessionId, null);
    }

    private OrgAuthResponse issueTokens(OrganisationUser user, String refreshToken,
                                         OrganisationClient client, String ipAddress,
                                         String userAgent, String sessionId, String hostname) {
        return issueTokens(user, refreshToken, sessionSettingsService.accessTokenTtl(user.getOrganisation(), client),
                ipAddress, userAgent, sessionId, hostname);
    }

    private OrgAuthResponse issueTokens(OrganisationUser user, String refreshToken, Duration accessTtl,
                                         String ipAddress, String userAgent, String sessionId) {
        return issueTokens(user, refreshToken, accessTtl, ipAddress, userAgent, sessionId, null);
    }

    private OrgAuthResponse issueTokens(OrganisationUser user, String refreshToken, Duration accessTtl,
                                         String ipAddress, String userAgent, String sessionId, String hostname) {
        Organisation organisation = user.getOrganisation();
        OrganisationSigningKey signingKey = orgKeyService.activeKey(organisation);
        String accessToken = orgJwtService.generateAccessToken(user, signingKey, accessTtl);
        Map<String, String> metadata = userFieldService.readMetadata(user.getId());
        List<OrgUserAction> actions = orgUserActions.of(user);
        return OrgAuthResponse.of(accessToken, refreshToken, accessTtl.toSeconds(), userMapper.toResponse(user, metadata), actions);
    }

    private void applyRegisterMetadata(OrgRegisterRequest request, OrganisationUser saved) {
        if (request.metadata() != null) userFieldService.setMetadata(saved, request.metadata());
    }
}
