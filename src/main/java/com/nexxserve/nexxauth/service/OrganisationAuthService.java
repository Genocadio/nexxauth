package com.nexxserve.nexxauth.service;

import com.nexxserve.nexxauth.dto.request.OrgLoginRequest;
import com.nexxserve.nexxauth.dto.request.OrgRegisterRequest;
import com.nexxserve.nexxauth.dto.response.OrgAuthResponse;
import com.nexxserve.nexxauth.entity.AuthType;
import com.nexxserve.nexxauth.entity.LogCategory;
import com.nexxserve.nexxauth.entity.LogLevel;
import com.nexxserve.nexxauth.entity.OrgIdentifierType;
import com.nexxserve.nexxauth.entity.Organisation;
import com.nexxserve.nexxauth.entity.OrganisationClient;
import com.nexxserve.nexxauth.entity.OrganisationRole;
import com.nexxserve.nexxauth.entity.OrganisationSigningKey;
import com.nexxserve.nexxauth.entity.OrganisationUser;
import com.nexxserve.nexxauth.entity.OrgUserAction;
import com.nexxserve.nexxauth.entity.Platform;
import com.nexxserve.nexxauth.exception.BadRequestException;
import com.nexxserve.nexxauth.exception.ConflictException;
import com.nexxserve.nexxauth.exception.InvalidCredentialsException;
import com.nexxserve.nexxauth.exception.PasswordExpiredException;
import com.nexxserve.nexxauth.exception.RefreshTokenException;
import com.nexxserve.nexxauth.exception.ResourceNotFoundException;
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
import java.util.List;
import java.util.Map;

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
    private final OrganisationSessionService sessionService;

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
                                   OrgUserActions orgUserActions, OrganisationSessionService sessionService) {
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
        this.sessionService = sessionService;
    }

    @Transactional
    public OrgAuthResponse register(String platformSlug, OrgRegisterRequest request, String clientId,
                                     String ipAddress, String userAgent) {
        OrganisationClient client = resolveClient(clientId);
        Organisation organisation = resolveOrganisation(platformSlug, request.organisationId(), client);
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
        audit.logPersisted(LogLevel.INFO, LogCategory.AUTH, AuthAuditService.ORG_REGISTER, identifierOf(saved),
                organisation.getSlug(), organisation.getId(), null);
        return issueTokens(saved, client, ipAddress, userAgent);
    }

    @Transactional
    public OrgAuthResponse login(String platformSlug, OrgLoginRequest request, String clientId,
                                  String ipAddress, String userAgent) {
        OrganisationClient client = resolveClient(clientId);
        Organisation organisation = resolveOrganisation(platformSlug, request.organisationId(), client);
        AuthType method = request.authType() != null ? request.authType() : AuthType.PASSWORD;
        return switch (method) {
            case PASSWORD -> passwordLogin(organisation, request, client, ipAddress, userAgent);
        };
    }

    private OrgAuthResponse passwordLogin(Organisation organisation, OrgLoginRequest request,
                                          OrganisationClient client, String ipAddress, String userAgent) {
        if (request.password() == null || request.password().isBlank())
            throw new BadRequestException("Password is required for the PASSWORD auth method");
        String identifier = request.identifier().trim();
        if (!authConfigService.configOf(organisation).isPasswordEnabled()) {
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
            throw new InvalidCredentialsException();
        }
        var config = authConfigService.configOf(organisation);
        if (authConfigService.isPasswordExpired(config, user)) {
            audit.logPersisted(LogLevel.WARN, LogCategory.SECURITY, AuthAuditService.ORG_LOGIN_FAILURE, identifier,
                    organisation.getSlug(), organisation.getId(), "password_expired");
            throw new PasswordExpiredException();
        }
        audit.logPersisted(LogLevel.INFO, LogCategory.AUTH, AuthAuditService.ORG_LOGIN_SUCCESS, identifier,
                organisation.getSlug(), organisation.getId(), null);
        return issueTokens(user, client, ipAddress, userAgent);
    }

    @Transactional
    public OrgAuthResponse refresh(String platformSlug, String rawRefreshToken,
                                    String ipAddress, String userAgent) {
        OrganisationUser resolved = refreshTokenService.resolveSubject(rawRefreshToken);
        if (orgUserActions.hasPendingGatingAction(resolved))
            throw new RefreshTokenException("Pending action required: change password");
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
                ipAddress, userAgent, existingSessionId);
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

    private OrganisationClient resolveClient(String clientId) {
        if (clientId == null || clientId.isBlank()) return null;
        return clientRepository.findByClientKey(clientId.trim()).orElse(null);
    }

    private Organisation resolveOrganisation(String platformSlug, Long organisationId, OrganisationClient client) {
        if (client != null) {
            Organisation o = client.getOrganisation();
            if (!o.getPlatform().getSlug().equals(platformSlug)) throw ResourceNotFoundException.of("Organisation", o.getId());
            return o;
        }
        if (organisationId == null) throw new BadRequestException("Organisation id is required when no X-Client-Id header is present");
        return findOrganisation(platformSlug, organisationId);
    }

    private Organisation findOrganisation(String platformSlug, Long organisationId) {
        Platform platform = platformAccess.findPlatform(platformSlug);
        return organisationRepository.findById(organisationId)
                .filter(o -> o.getPlatform().getId().equals(platform.getId()))
                .orElseThrow(() -> ResourceNotFoundException.of("Organisation", organisationId));
    }

    private OrgAuthResponse issueTokens(OrganisationUser user, OrganisationClient client,
                                         String ipAddress, String userAgent) {
        Organisation organisation = user.getOrganisation();
        if (orgUserActions.hasPendingGatingAction(user)) return issueTokens(user, null, OrgUserActions.GATING_ACCESS_TTL, null, null, null);
        refreshTokenService.enforceSessionLimit(organisation, user.getId(), client);
        // Dedup: if the user is logging in from the same IP+UA, reuse the existing session id
        String sessionId = sessionService.findExistingSessionId(user.getId(), ipAddress, userAgent);
        if (sessionId == null) {
            sessionId = sessionService.newSessionId();
        }
        return issueTokens(user, refreshTokenService.issueWithClient(user,
                sessionSettingsService.refreshTokenTtl(organisation, client),
                client != null ? client.getClientKey() : null,
                ipAddress, userAgent, sessionId), client, ipAddress, userAgent, sessionId);
    }

    private OrgAuthResponse issueTokens(OrganisationUser user, String refreshToken,
                                         OrganisationClient client, String ipAddress,
                                         String userAgent, String sessionId) {
        return issueTokens(user, refreshToken, sessionSettingsService.accessTokenTtl(user.getOrganisation(), client),
                ipAddress, userAgent, sessionId);
    }

    private OrgAuthResponse issueTokens(OrganisationUser user, String refreshToken, Duration accessTtl,
                                         String ipAddress, String userAgent, String sessionId) {
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
