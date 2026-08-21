package com.nexxserve.nauth.service;

import com.nexxserve.nauth.dto.request.OrgLoginRequest;
import com.nexxserve.nauth.dto.request.OrgRegisterRequest;
import com.nexxserve.nauth.dto.response.OrgAuthResponse;
import com.nexxserve.nauth.entity.AuthType;
import com.nexxserve.nauth.entity.OrgIdentifierType;
import com.nexxserve.nauth.entity.Organisation;
import com.nexxserve.nauth.entity.OrganisationClient;
import com.nexxserve.nauth.entity.OrganisationRole;
import com.nexxserve.nauth.entity.OrganisationSigningKey;
import com.nexxserve.nauth.entity.OrganisationUser;
import com.nexxserve.nauth.entity.OrgUserAction;
import com.nexxserve.nauth.entity.Platform;
import com.nexxserve.nauth.exception.BadRequestException;
import com.nexxserve.nauth.exception.ConflictException;
import com.nexxserve.nauth.exception.InvalidCredentialsException;
import com.nexxserve.nauth.exception.PasswordExpiredException;
import com.nexxserve.nauth.exception.RefreshTokenException;
import com.nexxserve.nauth.exception.ResourceNotFoundException;
import com.nexxserve.nauth.mapper.OrganisationUserMapper;
import com.nexxserve.nauth.repository.OrganisationClientRepository;
import com.nexxserve.nauth.repository.OrganisationRepository;
import com.nexxserve.nauth.repository.OrganisationRoleRepository;
import com.nexxserve.nauth.repository.OrganisationUserRepository;
import com.nexxserve.nauth.security.AuthTiming;
import com.nexxserve.nauth.security.OrgJwtService;
import com.nexxserve.nauth.util.Emails;
import com.nexxserve.nauth.util.Phones;
import com.nexxserve.nauth.util.Usernames;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Organisation-level authentication, completely independent of the platform
 * auth flow. Users log in with the organisation's identifier (username, or
 * email when the org uses email as username) and get access tokens signed by
 * the organisation's own RSA key. This layer never touches platform accounts.
 */
@Service
public class OrganisationAuthService {

    private final PlatformAccess platformAccess;
    private final OrganisationRepository organisationRepository;
    private final OrganisationClientRepository clientRepository;
    private final OrganisationRoleRepository roleRepository;
    private final OrganisationUserRepository userRepository;
    private final OrganisationRefreshTokenService refreshTokenService;
    private final OrgJwtService orgJwtService;
    private final OrgKeyService orgKeyService;
    private final PasswordEncoder passwordEncoder;
    private final OrganisationUserMapper userMapper;
    private final AuthAuditService audit;
    private final OrganisationAuthConfigService authConfigService;
    private final OrganisationSessionSettingsService sessionSettingsService;
    private final AuthTiming authTiming;
    private final OrganisationUserFieldService userFieldService;
    private final OrgUserActions orgUserActions;

    public OrganisationAuthService(PlatformAccess platformAccess, OrganisationRepository organisationRepository,
                                   OrganisationClientRepository clientRepository,
                                   OrganisationRoleRepository roleRepository,
                                   OrganisationUserRepository userRepository,
                                   OrganisationRefreshTokenService refreshTokenService, OrgJwtService orgJwtService,
                                   OrgKeyService orgKeyService,
                                   PasswordEncoder passwordEncoder, OrganisationUserMapper userMapper,
                                   AuthAuditService audit, OrganisationAuthConfigService authConfigService,
                                   OrganisationSessionSettingsService sessionSettingsService,
                                   AuthTiming authTiming, OrganisationUserFieldService userFieldService,
                                   OrgUserActions orgUserActions) {
        this.platformAccess = platformAccess;
        this.organisationRepository = organisationRepository;
        this.clientRepository = clientRepository;
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.refreshTokenService = refreshTokenService;
        this.orgJwtService = orgJwtService;
        this.orgKeyService = orgKeyService;
        this.passwordEncoder = passwordEncoder;
        this.userMapper = userMapper;
        this.audit = audit;
        this.authConfigService = authConfigService;
        this.sessionSettingsService = sessionSettingsService;
        this.authTiming = authTiming;
        this.userFieldService = userFieldService;
        this.orgUserActions = orgUserActions;
    }

    /** Creates an org user (no roles by default) and returns its tokens. The
     * organisation comes from the {@code X-Client-Id} client when present,
     * otherwise from {@code organisationId} in the body. */
    @Transactional
    public OrgAuthResponse register(String platformSlug, OrgRegisterRequest request,
                                    String clientId) {
        OrganisationClient client = resolveClient(clientId);
        Organisation organisation = resolveOrganisation(platformSlug, request.organisationId(), client);
        String email = normalizedEmail(request.email());
        String username = cleanedUsername(request.username());
        String phone = cleanedPhone(request.phone());

        // The organisation's configured sign-in identifiers: required ones must
        // be present, and at least one login-enabled identifier is needed so
        // the user can actually sign in.
        if (organisation.isEmailRequired() && email == null) {
            throw new ConflictException("A valid email is required for this organisation");
        }
        if (organisation.isUsernameRequired() && username == null) {
            throw new ConflictException("A username is required to register for this organisation");
        }
        if (organisation.isPhoneRequired() && phone == null) {
            throw new BadRequestException("A phone number is required for this organisation");
        }
        if (!hasLoginCapableIdentifier(organisation, email, username, phone)) {
            throw new BadRequestException(
                    "At least one login-enabled identifier (email, username or phone) is required");
        }
        assertIdentifiersFree(organisation, email, username, phone);

        OrganisationUser user = new OrganisationUser();
        user.setOrganisation(organisation);
        user.setFirstName(request.firstName());
        user.setLastName(cleanedName(request.lastName()));
        user.setEmail(email);
        user.setUsername(username);
        user.setPhone(phone);
        // Register configures password auth while it is enabled for the org;
        // when password auth is disabled a password is optional (the user gets
        // no auth until a method is enabled). The org's rules are validated
        // (length + history) before the user is persisted.
        if (request.password() != null && !request.password().isBlank()) {
            authConfigService.setPassword(user, request.password());
        } else if (authConfigService.configOf(organisation).isPasswordEnabled()) {
            throw new BadRequestException("Password is required for the PASSWORD auth method");
        }
        OrganisationUser saved = userRepository.save(user);
        applyRegisterMetadata(request, saved);
        // Roles marked as default are inherited automatically on register.
        assignDefaultRoles(organisation, saved);
        audit.log(AuthAuditService.ORG_REGISTER, identifierOf(saved), organisation.getSlug());
        return issueTokens(saved, client);
    }

    @Transactional
    public OrgAuthResponse login(String platformSlug, OrgLoginRequest request,
                                 String clientId) {
        OrganisationClient client = resolveClient(clientId);
        Organisation organisation = resolveOrganisation(platformSlug, request.organisationId(), client);
        // The requested authentication method. PASSWORD is the default; the
        // enum is the extension point for future methods (passkey, OTP, ...)
        // and the switch forces a case for each one.
        AuthType method = request.authType() != null ? request.authType() : AuthType.PASSWORD;
        return switch (method) {
            case PASSWORD -> passwordLogin(organisation, request, client);
        };
    }

    private OrgAuthResponse passwordLogin(Organisation organisation, OrgLoginRequest request,
                                          OrganisationClient client) {
        if (request.password() == null || request.password().isBlank()) {
            throw new BadRequestException("Password is required for the PASSWORD auth method");
        }
        String identifier = request.identifier().trim();
        // Password auth disabled for the org: every login fails identically to
        // bad credentials (no user enumeration, same timing burn).
        if (!authConfigService.configOf(organisation).isPasswordEnabled()) {
            audit.log(AuthAuditService.ORG_LOGIN_FAILURE, identifier, organisation.getSlug());
            authTiming.equalsUnknown(request.password());
            throw new InvalidCredentialsException();
        }
        OrganisationUser user = findByIdentifier(organisation, identifier, request.identifierType())
                .or(() -> userFieldService.findUserByLoginField(organisation, identifier))
                .orElseThrow(() -> {
                    audit.log(AuthAuditService.ORG_LOGIN_FAILURE, identifier, organisation.getSlug());
                    // Burn the same BCrypt cost as a real check so unknown and
                    // known identifiers answer in the same time.
                    authTiming.equalsUnknown(request.password());
                    return new InvalidCredentialsException();
                });
        // No auth configured (or not password auth): the user cannot log in.
        // Always run the BCrypt comparison (against a dummy for users without a
        // hash) so the three failure modes take the same time.
        String hash = user.getPasswordHash();
        boolean passwordMatches = hash != null
                ? passwordEncoder.matches(request.password(), hash)
                : authTiming.equalsUnknown(request.password());
        if (user.getAuthType() != AuthType.PASSWORD || !user.isEnabled() || !passwordMatches) {
            audit.log(AuthAuditService.ORG_LOGIN_FAILURE, identifier, organisation.getSlug());
            throw new InvalidCredentialsException();
        }
        var config = authConfigService.configOf(organisation);
        if (authConfigService.isPasswordExpired(config, user)) {
            audit.log(AuthAuditService.ORG_LOGIN_FAILURE, identifier, organisation.getSlug());
            throw new PasswordExpiredException();
        }
        audit.log(AuthAuditService.ORG_LOGIN_SUCCESS, identifier, organisation.getSlug());
        return issueTokens(user, client);
    }

    @Transactional
    public OrgAuthResponse refresh(String platformSlug, String rawRefreshToken) {
        // A pending gating action (e.g. forced password change) blocks session
        // refresh too: the user must resolve the action, then log in again.
        OrganisationUser resolved = refreshTokenService.resolveSubject(rawRefreshToken);
        if (orgUserActions.hasPendingGatingAction(resolved)) {
            throw new RefreshTokenException("Pending action required: change password");
        }
        // Resolve the client that originally issued this token so per-client
        // session settings apply on rotation.
        OrganisationClient client = refreshTokenService.clientOf(rawRefreshToken);
        var rotation = refreshTokenService.rotateWithSubject(rawRefreshToken,
                subject -> sessionSettingsService.refreshTokenTtl(subject.getOrganisation(), client));
        audit.log(AuthAuditService.ORG_REFRESH,
                identifierOf(rotation.subject()), rotation.subject().getOrganisation().getSlug());
        return issueTokens(rotation.subject(), rotation.newToken(), client);
    }

    @Transactional
    public void logout(String platformSlug, String rawRefreshToken) {
        // Attribute first (revoking would make the token unresolvable), then
        // revoke. Idempotent: an unknown/already-revoked token still logs out
        // with a 204 and simply has no audit actor.
        refreshTokenService.findSubjectForAudit(rawRefreshToken).ifPresent(user ->
                audit.log(AuthAuditService.ORG_LOGOUT,
                        identifierOf(user), user.getOrganisation().getSlug()));
        refreshTokenService.revoke(rawRefreshToken);
    }

    /** Assigns every role the organisation marked as default to the newly
     * registered user. The user stays managed, so the collection change is
     * flushed with the transaction. */
    private void assignDefaultRoles(Organisation organisation, OrganisationUser user) {
        List<OrganisationRole> defaults =
                roleRepository.findByOrganisationIdAndDefaultRoleTrue(organisation.getId());
        if (!defaults.isEmpty()) {
            // Mutable set: Hibernate syncs the join table against it on flush.
            user.setRoles(new java.util.HashSet<>(defaults));
        }
    }

    private String identifierOf(OrganisationUser user) {
        // First non-null identifier, so phone-only users stay attributable.
        return user.getUsername() != null ? user.getUsername()
                : user.getEmail() != null ? user.getEmail()
                : user.getPhone() != null ? user.getPhone() : "unknown";
    }

    private void assertIdentifiersFree(Organisation organisation, String email, String username, String phone) {
        if (email != null && userRepository.existsByOrganisationIdAndEmail(organisation.getId(), email)) {
            throw new ConflictException("An organisation user with email " + email
                    + " already exists in this organisation");
        }
        if (username != null && userRepository.existsByOrganisationIdAndUsername(organisation.getId(), username)) {
            throw new ConflictException("An organisation user with username " + username
                    + " already exists in this organisation");
        }
        if (phone != null && userRepository.existsByOrganisationIdAndPhone(organisation.getId(), phone)) {
            throw new ConflictException("An organisation user with phone " + phone
                    + " already exists in this organisation");
        }
    }

    /** null/blank -> null; otherwise trimmed. */
    private String cleanedName(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** null/blank -> null; otherwise trimmed + normalized email. */
    private String normalizedEmail(String email) {
        if (email == null) {
            return null;
        }
        String normalized = Emails.normalize(email);
        return normalized.isBlank() ? null : normalized;
    }

    /** null/blank -> null; otherwise trimmed + lowercased (Bob == bob). */
    private String cleanedUsername(String value) {
        if (value == null) {
            return null;
        }
        String normalized = Usernames.normalize(value);
        return normalized.isEmpty() ? null : normalized;
    }

    /** null/blank -> null; otherwise trimmed with separators stripped. */
    private String cleanedPhone(String value) {
        if (value == null) {
            return null;
        }
        String normalized = Phones.normalize(value);
        return normalized.isEmpty() ? null : normalized;
    }

    /** True when at least one provided identifier is enabled for login. */
    private boolean hasLoginCapableIdentifier(Organisation organisation, String email,
                                              String username, String phone) {
        return (organisation.isEmailCanLogin() && email != null)
                || (organisation.isUsernameCanLogin() && username != null)
                || (organisation.isPhoneCanLogin() && phone != null);
    }

    /** Looks up the user by the requested identifier type, or by each enabled
     * identifier in order when the type is omitted (then login-enabled fields). */
    private java.util.Optional<OrganisationUser> findByIdentifier(Organisation organisation,
                                                                  String identifier,
                                                                  OrgIdentifierType identifierType) {
        if (identifierType != null) {
            return switch (identifierType) {
                case EMAIL -> organisation.isEmailCanLogin()
                        ? userRepository.findWithRolesByOrganisationIdAndEmail(organisation.getId(),
                        Emails.normalize(identifier))
                        : java.util.Optional.empty();
                case USERNAME -> organisation.isUsernameCanLogin()
                        ? userRepository.findWithRolesByOrganisationIdAndUsername(organisation.getId(),
                        Usernames.normalize(identifier))
                        : java.util.Optional.empty();
                case PHONE -> organisation.isPhoneCanLogin()
                        ? userRepository.findWithRolesByOrganisationIdAndPhone(organisation.getId(),
                        Phones.normalize(identifier))
                        : java.util.Optional.empty();
            };
        }
        java.util.Optional<OrganisationUser> direct = enabledIdentifierLookup(organisation, identifier);
        if (direct.isPresent()) {
            return direct;
        }
        return userFieldService.findUserByLoginField(organisation, identifier);
    }

    /** Tries username, then email, then phone — only the enabled ones. */
    private java.util.Optional<OrganisationUser> enabledIdentifierLookup(Organisation organisation,
                                                                         String identifier) {
        if (organisation.isUsernameCanLogin()) {
            java.util.Optional<OrganisationUser> user =
                    userRepository.findWithRolesByOrganisationIdAndUsername(organisation.getId(),
                            Usernames.normalize(identifier));
            if (user.isPresent()) {
                return user;
            }
        }
        if (organisation.isEmailCanLogin()) {
            java.util.Optional<OrganisationUser> user =
                    userRepository.findWithRolesByOrganisationIdAndEmail(organisation.getId(),
                            Emails.normalize(identifier));
            if (user.isPresent()) {
                return user;
            }
        }
        if (organisation.isPhoneCanLogin()) {
            java.util.Optional<OrganisationUser> user =
                    userRepository.findWithRolesByOrganisationIdAndPhone(organisation.getId(),
                            Phones.normalize(identifier));
            if (user.isPresent()) {
                return user;
            }
        }
        return java.util.Optional.empty();
    }

    /** Resolves the {@code X-Client-Id} header to a client within this
     * transaction, or {@code null} when absent. Unknown or blank keys (already
     * rejected by the client filter before this point) fall back to
     * body-based identification. */
    private OrganisationClient resolveClient(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            return null;
        }
        return clientRepository.findByClientKey(clientId.trim()).orElse(null);
    }

    /** Resolves the organisation: the client's organisation when an
     * {@code X-Client-Id} client is present (body {@code organisationId} is
     * ignored — the client is authoritative), otherwise the body's
     * {@code organisationId} (required for server-side/platform-user flows). */
    private Organisation resolveOrganisation(String platformSlug, Long organisationId,
                                             OrganisationClient client) {
        if (client != null) {
            Organisation organisation = client.getOrganisation();
            if (!organisation.getPlatform().getSlug().equals(platformSlug)) {
                throw ResourceNotFoundException.of("Organisation", organisation.getId());
            }
            return organisation;
        }
        if (organisationId == null) {
            throw new BadRequestException(
                    "Organisation id is required when no X-Client-Id header is present");
        }
        return findOrganisation(platformSlug, organisationId);
    }

    private Organisation findOrganisation(String platformSlug, Long organisationId) {
        Platform platform = platformAccess.findPlatform(platformSlug);
        return organisationRepository.findById(organisationId)
                .filter(organisation -> organisation.getPlatform().getId().equals(platform.getId()))
                .orElseThrow(() -> ResourceNotFoundException.of("Organisation", organisationId));
    }

    private OrgAuthResponse issueTokens(OrganisationUser user, OrganisationClient client) {
        Organisation organisation = user.getOrganisation();
        if (orgUserActions.hasPendingGatingAction(user)) {
            // A gating action (CHANGE_PASSWORD) is pending: the session is
            // restricted - a short-lived access token to complete the action,
            // no refresh token, regardless of the org's session settings.
            return issueTokens(user, null, OrgUserActions.GATING_ACCESS_TTL);
        }
        // A new session is about to be issued: evict the oldest sessions if the
        // user is at the org's concurrent-session limit.
        refreshTokenService.enforceSessionLimit(organisation, user.getId(), client);
        return issueTokens(user,
                refreshTokenService.issueWithClient(user, sessionSettingsService.refreshTokenTtl(organisation, client),
                        client != null ? client.getClientKey() : null),
                client);
    }

    private OrgAuthResponse issueTokens(OrganisationUser user, String refreshToken, OrganisationClient client) {
        return issueTokens(user, refreshToken, sessionSettingsService.accessTokenTtl(user.getOrganisation(), client));
    }

    private OrgAuthResponse issueTokens(OrganisationUser user, String refreshToken, Duration accessTtl) {
        Organisation organisation = user.getOrganisation();
        OrganisationSigningKey signingKey = orgKeyService.activeKey(organisation);
        String accessToken = orgJwtService.generateAccessToken(user, signingKey, accessTtl);
        Map<String, String> metadata = userFieldService.readMetadata(user.getId());
        List<OrgUserAction> actions = orgUserActions.of(user);
        return OrgAuthResponse.of(accessToken, refreshToken, accessTtl.toSeconds(),
                userMapper.toResponse(user, metadata), actions);
    }

    private void applyRegisterMetadata(OrgRegisterRequest request, OrganisationUser saved) {
        if (request.metadata() != null) {
            userFieldService.setMetadata(saved, request.metadata());
        }
    }
}
