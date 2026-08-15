package com.nexxserve.nexxauth.service;

import com.nexxserve.nexxauth.dto.request.OrgLoginRequest;
import com.nexxserve.nexxauth.dto.request.OrgRegisterRequest;
import com.nexxserve.nexxauth.dto.response.OrgAuthResponse;
import com.nexxserve.nexxauth.entity.AuthType;
import com.nexxserve.nexxauth.entity.Organisation;
import com.nexxserve.nexxauth.entity.OrganisationSigningKey;
import com.nexxserve.nexxauth.entity.OrganisationUser;
import com.nexxserve.nexxauth.entity.Platform;
import com.nexxserve.nexxauth.exception.ConflictException;
import com.nexxserve.nexxauth.exception.InvalidCredentialsException;
import com.nexxserve.nexxauth.exception.PasswordExpiredException;
import com.nexxserve.nexxauth.exception.ResourceNotFoundException;
import com.nexxserve.nexxauth.mapper.OrganisationUserMapper;
import com.nexxserve.nexxauth.repository.OrganisationRepository;
import com.nexxserve.nexxauth.repository.OrganisationUserRepository;
import com.nexxserve.nexxauth.security.AuthTiming;
import com.nexxserve.nexxauth.security.OrgJwtService;
import com.nexxserve.nexxauth.util.Emails;
import com.nexxserve.nexxauth.util.Usernames;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

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

    public OrganisationAuthService(PlatformAccess platformAccess, OrganisationRepository organisationRepository,
                                   OrganisationUserRepository userRepository,
                                   OrganisationRefreshTokenService refreshTokenService, OrgJwtService orgJwtService,
                                   OrgKeyService orgKeyService,
                                   PasswordEncoder passwordEncoder, OrganisationUserMapper userMapper,
                                   AuthAuditService audit, OrganisationAuthConfigService authConfigService,
                                   OrganisationSessionSettingsService sessionSettingsService,
                                   AuthTiming authTiming, OrganisationUserFieldService userFieldService) {
        this.platformAccess = platformAccess;
        this.organisationRepository = organisationRepository;
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
    }

    /** Creates an org user (no roles by default) and returns its tokens. */
    @Transactional
    public OrgAuthResponse register(String platformSlug, OrgRegisterRequest request) {
        Organisation organisation = findOrganisation(platformSlug, request.organisationId());
        String identifier = request.identifier().trim();
        if (organisation.isUseEmailAsUsername()) {
            String email = Emails.normalize(identifier);
            if (email.isBlank()) {
                throw new ConflictException("A valid email is required as the username for this organisation");
            }
            if (userRepository.existsByOrganisationIdAndEmail(organisation.getId(), email)) {
                throw new ConflictException("An organisation user with email " + email
                        + " already exists in this organisation");
            }
            OrganisationUser user = new OrganisationUser();
            user.setOrganisation(organisation);
            user.setFirstName(request.firstName());
            user.setLastName(request.lastName());
            user.setEmail(email);
            // Register always configures password auth; the org's rules are
            // validated (length + history) before the user is persisted.
            authConfigService.setPassword(user, request.password());
            OrganisationUser saved = userRepository.save(user);
            applyRegisterMetadata(request, saved);
            audit.log(AuthAuditService.ORG_REGISTER, email, organisation.getSlug());
            return issueTokens(saved);
        }
        String username = Usernames.normalize(identifier);
        if (userRepository.existsByOrganisationIdAndUsername(organisation.getId(), username)) {
            throw new ConflictException("An organisation user with username " + username
                    + " already exists in this organisation");
        }
        OrganisationUser user = new OrganisationUser();
        user.setOrganisation(organisation);
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setUsername(username);
        authConfigService.setPassword(user, request.password());
        OrganisationUser saved = userRepository.save(user);
        applyRegisterMetadata(request, saved);
        audit.log(AuthAuditService.ORG_REGISTER, identifier, organisation.getSlug());
        return issueTokens(saved);
    }

    @Transactional
    public OrgAuthResponse login(String platformSlug, OrgLoginRequest request) {
        Organisation organisation = findOrganisation(platformSlug, request.organisationId());
        String identifier = request.identifier().trim();
        OrganisationUser user = findByIdentifier(organisation, identifier)
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
        return issueTokens(user);
    }

    @Transactional
    public OrgAuthResponse refresh(String platformSlug, String rawRefreshToken) {
        var rotation = refreshTokenService.rotateWithSubject(rawRefreshToken,
                subject -> sessionSettingsService.refreshTokenTtl(subject.getOrganisation()));
        audit.log(AuthAuditService.ORG_REFRESH,
                identifierOf(rotation.subject()), rotation.subject().getOrganisation().getSlug());
        return issueTokens(rotation.subject(), rotation.newToken());
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

    private String identifierOf(OrganisationUser user) {
        return user.getUsername() != null ? user.getUsername() : user.getEmail();
    }

    private java.util.Optional<OrganisationUser> findByIdentifier(Organisation organisation, String identifier) {
        if (organisation.isUseEmailAsUsername()) {
            return userRepository.findWithRolesByOrganisationIdAndEmail(organisation.getId(),
                    Emails.normalize(identifier));
        }
        return userRepository.findWithRolesByOrganisationIdAndUsername(organisation.getId(),
                Usernames.normalize(identifier));
    }

    private Organisation findOrganisation(String platformSlug, Long organisationId) {
        Platform platform = platformAccess.findPlatform(platformSlug);
        return organisationRepository.findById(organisationId)
                .filter(organisation -> organisation.getPlatform().getId().equals(platform.getId()))
                .orElseThrow(() -> ResourceNotFoundException.of("Organisation", organisationId));
    }

    private OrgAuthResponse issueTokens(OrganisationUser user) {
        Organisation organisation = user.getOrganisation();
        // A new session is about to be issued: evict the oldest sessions if the
        // user is at the org's concurrent-session limit.
        refreshTokenService.enforceSessionLimit(organisation, user.getId());
        return issueTokens(user,
                refreshTokenService.issue(user, sessionSettingsService.refreshTokenTtl(organisation)));
    }

    private OrgAuthResponse issueTokens(OrganisationUser user, String refreshToken) {
        Organisation organisation = user.getOrganisation();
        Duration accessTtl = sessionSettingsService.accessTokenTtl(organisation);
        OrganisationSigningKey signingKey = orgKeyService.activeKey(organisation);
        String accessToken = orgJwtService.generateAccessToken(user, signingKey, accessTtl);
        return OrgAuthResponse.of(accessToken, refreshToken, accessTtl.toSeconds(),
                userMapper.toResponse(user, userFieldService.readMetadata(user.getId())));
    }

    private void applyRegisterMetadata(OrgRegisterRequest request, OrganisationUser saved) {
        if (request.metadata() != null) {
            userFieldService.setMetadata(saved, request.metadata());
        }
    }
}
