package com.nexxserve.nexxauth.service;

import com.nexxserve.nexxauth.dto.request.PasswordResetConfirmRequest;
import com.nexxserve.nexxauth.dto.request.VerificationRequest;
import com.nexxserve.nexxauth.dto.request.VerificationVerifyRequest;
import com.nexxserve.nexxauth.dto.response.LoginChallenge;
import com.nexxserve.nexxauth.dto.response.VerificationRequestResponse;
import com.nexxserve.nexxauth.entity.LogCategory;
import com.nexxserve.nexxauth.entity.LogLevel;
import com.nexxserve.nexxauth.entity.Organisation;
import com.nexxserve.nexxauth.entity.OrganisationAuthConfig;
import com.nexxserve.nexxauth.entity.OrganisationClient;
import com.nexxserve.nexxauth.entity.OrganisationUser;
import com.nexxserve.nexxauth.entity.OrganisationVerificationToken;
import com.nexxserve.nexxauth.entity.VerificationChannel;
import com.nexxserve.nexxauth.entity.VerificationDelivery;
import com.nexxserve.nexxauth.entity.VerificationPurpose;
import com.nexxserve.nexxauth.exception.BadRequestException;
import com.nexxserve.nexxauth.exception.InvalidCredentialsException;
import com.nexxserve.nexxauth.exception.ResourceNotFoundException;
import com.nexxserve.nexxauth.exception.ServiceUnavailableException;
import com.nexxserve.nexxauth.repository.OrganisationClientRepository;
import com.nexxserve.nexxauth.repository.OrganisationRepository;
import com.nexxserve.nexxauth.repository.OrganisationUserRepository;
import com.nexxserve.nexxauth.repository.OrganisationVerificationTokenRepository;
import com.nexxserve.nexxauth.util.Emails;
import com.nexxserve.nexxauth.util.Phones;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.Optional;

/**
 * Org-level verification via nexxbotify: request/confirm numeric OTPs and
 * magic links delivered to an email address or phone number. Powers three
 * per-org features — email verification, phone verification and password
 * reset — each independently toggled in {@link OrganisationAuthConfig}.
 * <p>
 * Only the SHA-256 hash of a code/link is persisted; the raw value travels to
 * the user through nexxbotify and is never stored. Values are single-use,
 * time-boxed, throttled per identifier, and invalidated after
 * {@code app.verification.max-attempts} failed attempts.
 */
@Service
public class OrganisationVerificationService {

    private static final Logger log = LoggerFactory.getLogger(OrganisationVerificationService.class);

    private final OrganisationRepository organisationRepository;
    private final OrganisationClientRepository clientRepository;
    private final OrganisationUserRepository userRepository;
    private final OrganisationVerificationTokenRepository tokenRepository;
    private final OrganisationAuthConfigService authConfigService;
    private final OrganisationRefreshTokenService refreshTokenService;
    private final NexxbotifyClient nexxbotifyClient;
    private final VerificationProperties properties;
    private final AuthAuditService audit;
    private final SecureRandom random = new SecureRandom();
    private final String jwtSecret;

    /** Claims on the short-lived login-challenge token (a stateless HMAC JWT
     * that identifies the pending login; the actual code check still reads the
     * verification-token row). */
    private static final String CLAIM_ORG_ID = "orgId";
    private static final String CLAIM_CHANNEL = "channel";
    private static final String CLAIM_PURPOSE = "purpose";
    private static final String CLAIM_IDENTIFIER = "identifier";

    public OrganisationVerificationService(OrganisationRepository organisationRepository,
                                           OrganisationClientRepository clientRepository,
                                           OrganisationUserRepository userRepository,
                                           OrganisationVerificationTokenRepository tokenRepository,
                                           OrganisationAuthConfigService authConfigService,
                                           OrganisationRefreshTokenService refreshTokenService,
                                           NexxbotifyClient nexxbotifyClient,
                                           VerificationProperties properties,
                                           AuthAuditService audit,
                                           @Value("${app.jwt.secret}") String jwtSecret) {
        this.organisationRepository = organisationRepository;
        this.clientRepository = clientRepository;
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.authConfigService = authConfigService;
        this.refreshTokenService = refreshTokenService;
        this.nexxbotifyClient = nexxbotifyClient;
        this.properties = properties;
        this.audit = audit;
        this.jwtSecret = jwtSecret;
    }

    // ---------------------------------------------------------------------
    // Request a code / link
    // ---------------------------------------------------------------------

    @Transactional
    public VerificationRequestResponse request(String platformSlug, VerificationRequest request,
                                               String clientId) {
        Organisation organisation = resolveOrganisation(platformSlug, request.organisationId(), clientId);
        VerificationPurpose purpose = request.purpose();
        requireFeature(organisation, purpose);
        requireNotifier();

        VerificationChannel channel = request.channel();
        validatePurposeVsChannel(purpose, channel);
        // Default delivery comes from the org's verification mode when the
        // request does not pick one. A login OTP is only ever a code typed
        // back into the login form.
        VerificationDelivery delivery = request.delivery() != null
                ? request.delivery()
                : authConfigService.configOf(organisation).getVerificationMode();
        if (purpose == VerificationPurpose.LOGIN_OTP && delivery != VerificationDelivery.OTP) {
            throw new BadRequestException("OTP login only supports numeric codes");
        }

        String identifier = normalize(channel, request.identifier());
        OrganisationUser user = findUser(organisation, identifier, purpose);

        Instant now = Instant.now();
        tokenRepository.findFirstByOrganisationIdAndPurposeAndChannelAndIdentifierAndConsumedAtIsNullOrderByCreatedAtDesc(
                        organisation.getId(), purpose, channel, identifier)
                .ifPresent(active -> enforceResendInterval(active, now));

        String value = generate(delivery);
        OrganisationVerificationToken token = new OrganisationVerificationToken();
        token.setOrganisation(organisation);
        token.setOrganisationUser(user);
        token.setPurpose(purpose);
        token.setChannel(channel);
        token.setDelivery(delivery);
        token.setIdentifier(identifier);
        token.setTokenHash(hashToken(value));
        token.setExpiresAt(now.plus(properties.ttlFor(delivery)));
        token.setSentAt(now);
        // One active value per (org, purpose, channel, identifier): supersede any old one.
        tokenRepository.deleteActiveForIdentifier(organisation.getId(), purpose, channel, identifier);
        tokenRepository.save(token);

        deliver(organisation, user, delivery, channel, identifier, value);
        audit.logPersisted(LogLevel.INFO, LogCategory.AUTH, AuthAuditService.ORG_VERIFICATION_SENT,
                identifier, organisation.getSlug(), organisation.getId(),
                purpose.name() + " " + channel.name() + " " + delivery.name());

        return new VerificationRequestResponse(purpose, channel, delivery, identifier,
                properties.ttlFor(delivery).toSeconds());
    }

    // ---------------------------------------------------------------------
    // Verify an OTP (email / phone verification)
    // ---------------------------------------------------------------------

    /** Confirms a numeric OTP for {@code EMAIL_VERIFICATION} or
     * {@code PHONE_VERIFICATION}; successful verification stamps the address
     * as verified on the owning user. */
    @Transactional
    public void verifyOtp(String platformSlug, VerificationVerifyRequest request, String clientId) {
        VerificationPurpose purpose = request.purpose();
        if (purpose != VerificationPurpose.EMAIL_VERIFICATION
                && purpose != VerificationPurpose.PHONE_VERIFICATION) {
            throw new BadRequestException("This endpoint only confirms address verification codes");
        }
        Organisation organisation = resolveOrganisation(platformSlug, request.organisationId(), clientId);
        requireFeature(organisation, purpose);

        VerificationChannel channel = channelFor(purpose);
        String identifier = normalize(channel, request.identifier());
        OrganisationUser user = findUser(organisation, identifier, purpose);
        if (user == null) {
            throw new BadRequestException("No account found for this " + channel.name().toLowerCase());
        }

        Instant now = Instant.now();
        OrganisationVerificationToken token = tokenRepository.findActive(
                        organisation.getId(), purpose, channel, identifier, now)
                .orElseThrow(() -> new BadRequestException("Invalid or expired code"));
        if (!MessageDigest.isEqual(hashToken(request.code()).getBytes(),
                token.getTokenHash().getBytes())) {
            recordFailedAttempt(token, now);
            throw new BadRequestException("Invalid or expired code");
        }

        token.setConsumedAt(now);
        markVerified(user, purpose, now);
        audit.logPersisted(LogLevel.INFO, LogCategory.AUTH, AuthAuditService.ORG_IDENTIFIER_VERIFIED,
                identifier, organisation.getSlug(), organisation.getId(),
                purpose.name() + " " + channel.name());
    }

    /** Confirms a magic link for {@code EMAIL_VERIFICATION} / {@code PHONE_VERIFICATION}. */
    @Transactional
    public void completeViaLink(String platformSlug, String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new BadRequestException("Token is required");
        }
        Instant now = Instant.now();
        OrganisationVerificationToken token = tokenRepository.findFirstByTokenHashAndConsumedAtIsNull(hashToken(rawToken))
                .orElseThrow(() -> new BadRequestException("Invalid or expired link"));
        if (token.isExpired(now)) {
            throw new BadRequestException("Invalid or expired link");
        }
        VerificationPurpose purpose = token.getPurpose();
        if (purpose != VerificationPurpose.EMAIL_VERIFICATION
                && purpose != VerificationPurpose.PHONE_VERIFICATION) {
            throw new BadRequestException("This link is not for address verification");
        }
        // The link may be opened from any client: only the platform slug must match.
        if (!token.getOrganisation().getPlatform().getSlug().equals(platformSlug)) {
            throw new BadRequestException("Invalid or expired link");
        }
        OrganisationUser user = token.getOrganisationUser();
        if (user == null || !user.getOrganisation().getId().equals(token.getOrganisation().getId())) {
            throw new BadRequestException("Invalid or expired link");
        }
        token.setConsumedAt(now);
        markVerified(user, purpose, now);
        audit.logPersisted(LogLevel.INFO, LogCategory.AUTH, AuthAuditService.ORG_IDENTIFIER_VERIFIED,
                token.getIdentifier(), token.getOrganisation().getSlug(),
                token.getOrganisation().getId(), purpose.name() + " via link");
    }

    // ---------------------------------------------------------------------
    // Password reset
    // ---------------------------------------------------------------------

    /** Final step of a password reset: validates the received code/link for
     * the identifier and sets a new password, revoking all the user's sessions
     * so concurrent sign-ins with the old password are cut off. */
    @Transactional
    public void confirmPasswordReset(String platformSlug, PasswordResetConfirmRequest request, String clientId) {
        Organisation organisation = resolveOrganisation(platformSlug, request.organisationId(), clientId);
        requireFeature(organisation, VerificationPurpose.PASSWORD_RESET);
        VerificationChannel channel = request.channel();
        String identifier = normalize(channel, request.identifier());

        Instant now = Instant.now();
        OrganisationVerificationToken token = tokenRepository.findActive(
                        organisation.getId(), VerificationPurpose.PASSWORD_RESET, channel, identifier, now)
                .orElseThrow(() -> new BadRequestException("Invalid or expired reset code"));
        if (!MessageDigest.isEqual(hashToken(request.token()).getBytes(),
                token.getTokenHash().getBytes())) {
            recordFailedAttempt(token, now);
            throw new BadRequestException("Invalid or expired reset code");
        }

        OrganisationUser user = findUserForPasswordReset(organisation, identifier);
        if (user == null || !user.isEnabled()) {
            audit.logPersisted(LogLevel.WARN, LogCategory.SECURITY, AuthAuditService.ORG_PASSWORD_RESET_FAILURE,
                    identifier, organisation.getSlug(), organisation.getId(), "no_account");
            throw new BadRequestException("No account found for this identifier");
        }

        token.setConsumedAt(now);
        authConfigService.setPassword(user, request.newPassword());
        refreshTokenService.revokeAllForUser(user.getId());
        audit.logPersisted(LogLevel.INFO, LogCategory.AUTH, AuthAuditService.ORG_PASSWORD_RESET,
                identifier, organisation.getSlug(), organisation.getId(),
                "channel=" + channel.name());
    }

    // ---------------------------------------------------------------------
    // Login challenges (2FA / verify at next login)
    // ---------------------------------------------------------------------

    /** A login challenge is always a numeric OTP, sent by the server as part
     * of the login. {@code purpose} is a login obligation to resolve:
     * {@code TWO_FACTOR}, or an address verification forced at next login
     * ({@code EMAIL_VERIFICATION} / {@code PHONE_VERIFICATION}). */
    @Transactional
    public LoginChallenge startLoginChallenge(Organisation organisation, OrganisationUser user,
                                              VerificationPurpose purpose, VerificationChannel channel) {
        requireNotifier();
        String identifier = normalizedIdentifier(user, channel);
        Instant now = Instant.now();
        tokenRepository.findFirstByOrganisationIdAndPurposeAndChannelAndIdentifierAndConsumedAtIsNullOrderByCreatedAtDesc(
                        organisation.getId(), purpose, channel, identifier)
                .ifPresent(active -> enforceResendInterval(active, now));

        String value = generateOtp(properties.getOtpLength());

        OrganisationVerificationToken token = new OrganisationVerificationToken();
        token.setOrganisation(organisation);
        token.setOrganisationUser(user);
        token.setPurpose(purpose);
        token.setChannel(channel);
        token.setDelivery(VerificationDelivery.OTP);
        token.setIdentifier(identifier);
        token.setTokenHash(hashToken(value));
        token.setExpiresAt(now.plus(properties.getOtpTtl()));
        token.setSentAt(now);
        tokenRepository.deleteActiveForIdentifier(organisation.getId(), purpose, channel, identifier);
        tokenRepository.save(token);

        deliver(organisation, user, VerificationDelivery.OTP, channel, identifier, value);
        audit.logPersisted(LogLevel.INFO, LogCategory.AUTH, AuthAuditService.ORG_VERIFICATION_SENT,
                identifier, organisation.getSlug(), organisation.getId(),
                "login_challenge " + purpose.name() + " " + channel.name());

        Duration ttl = properties.getChallengeTtl();
        String rawToken = signChallenge(organisation, user, purpose, channel, identifier, ttl);
        return new LoginChallenge(rawToken, purpose, channel, VerificationDelivery.OTP, ttl.toSeconds());
    }

    /** Validates the code for a pending login challenge. The challenge token
     * names the user, organisation, channel and purpose; the code check still
     * goes through the hashed verification-token row, so attempts and expiry
     * behave exactly like every other OTP. Returns the user being signed in
     * and the purpose just satisfied. */
    @Transactional
    public ResolvedChallenge resolveLoginChallenge(String rawChallenge, String code) {
        Claims claims = parseChallenge(rawChallenge);
        Organisation organisation = organisationRepository.findById(orgId(claims))
                .orElseThrow(InvalidCredentialsException::new);
        VerificationPurpose purpose = VerificationPurpose.valueOf(claims.get(CLAIM_PURPOSE, String.class));
        VerificationChannel channel = VerificationChannel.valueOf(claims.get(CLAIM_CHANNEL, String.class));
        Long userId = Long.valueOf(claims.getSubject());
        String identifier = claims.get(CLAIM_IDENTIFIER, String.class);

        Instant now = Instant.now();
        OrganisationVerificationToken token = tokenRepository.findActive(organisation.getId(),
                        purpose, channel, identifier, now)
                .orElseThrow(() -> {
                    audit.logPersisted(LogLevel.WARN, LogCategory.SECURITY, AuthAuditService.ORG_LOGIN_FAILURE,
                            identifier, organisation.getSlug(), organisation.getId(),
                            purpose + "_no_token");
                    return new InvalidCredentialsException();
                });
        if (!MessageDigest.isEqual(
                hashToken(code.trim()).getBytes(StandardCharsets.UTF_8),
                token.getTokenHash().getBytes(StandardCharsets.UTF_8))) {
            audit.logPersisted(LogLevel.WARN, LogCategory.SECURITY, AuthAuditService.ORG_LOGIN_FAILURE,
                    identifier, organisation.getSlug(), organisation.getId(),
                    purpose + "_invalid");
            recordFailedAttempt(token, now);
            throw new InvalidCredentialsException();
        }
        OrganisationUser user = userRepository.findById(userId)
                .orElseThrow(InvalidCredentialsException::new);
        if (!user.getOrganisation().getId().equals(organisation.getId())) {
            throw new InvalidCredentialsException();
        }
        token.setConsumedAt(now);
        tokenRepository.save(token);
        return new ResolvedChallenge(user, purpose, channel, identifier);
    }

    /** Marks the identifier of {@code user} verified for a just-completed
     * login challenge and clears the matching "verify at next login" flag. */
    @Transactional
    public void completeChallengeVerification(OrganisationUser user, VerificationPurpose purpose, Instant now) {
        if (purpose == VerificationPurpose.EMAIL_VERIFICATION) {
            user.setEmailVerifiedAt(now);
            user.setRequireEmailVerificationAtNextLogin(false);
        } else if (purpose == VerificationPurpose.PHONE_VERIFICATION) {
            user.setPhoneVerifiedAt(now);
            user.setRequirePhoneVerificationAtNextLogin(false);
        }
        user.bumpDataHash();
        userRepository.save(user);
    }

    record ResolvedChallenge(OrganisationUser user, VerificationPurpose purpose,
                             VerificationChannel channel, String identifier) {
    }

    // ---------------------------------------------------------------------
    // Verification required at registration
    // ---------------------------------------------------------------------

    /** When the organisation requires email/phone verification at registration,
     * a code or link (the org's verification mode) is sent immediately for each
     * unverified identifier so the gated session can be unlocked right away. */
    @Transactional
    public void sendOnRegisterVerification(Organisation organisation, OrganisationUser user) {
        OrganisationAuthConfig config = authConfigService.configOf(organisation);
        boolean sendEmail = config.isRequireEmailVerificationOnRegister()
                && user.getEmail() != null && !user.getEmail().isBlank() && user.getEmailVerifiedAt() == null;
        boolean sendPhone = config.isRequirePhoneVerificationOnRegister()
                && user.getPhone() != null && !user.getPhone().isBlank() && user.getPhoneVerifiedAt() == null;
        if (!sendEmail && !sendPhone) {
            return;
        }
        if (!nexxbotifyClient.isConfigured()) {
            // The org asks for verification at registration, but without a
            // notification service there is nothing to send: skip silently so
            // registration still succeeds; the account simply stays unverified
            // and the verification features remain locked.
            log.warn("Skipping register verification for org {}: notification service is not configured",
                    organisation.getSlug());
            return;
        }
        VerificationDelivery mode = config.getVerificationMode();
        if (sendEmail) {
            emitVerification(organisation, user, VerificationPurpose.EMAIL_VERIFICATION,
                    VerificationChannel.EMAIL, mode);
        }
        if (sendPhone) {
            emitVerification(organisation, user, VerificationPurpose.PHONE_VERIFICATION,
                    VerificationChannel.SMS, mode);
        }
    }

    /** Stores and delivers a fresh verification for {@code user} without the
     * login-challenge wrapper: a plain code or magic link on the given channel. */
    private void emitVerification(Organisation organisation, OrganisationUser user,
                                  VerificationPurpose purpose, VerificationChannel channel,
                                  VerificationDelivery delivery) {
        String identifier = normalizedIdentifier(user, channel);
        String value = generate(delivery);
        OrganisationVerificationToken token = new OrganisationVerificationToken();
        token.setOrganisation(organisation);
        token.setOrganisationUser(user);
        token.setPurpose(purpose);
        token.setChannel(channel);
        token.setDelivery(delivery);
        token.setIdentifier(identifier);
        token.setTokenHash(hashToken(value));
        token.setExpiresAt(Instant.now().plus(delivery == VerificationDelivery.LINK ? properties.getLinkTtl()
                : properties.getOtpTtl()));
        token.setSentAt(Instant.now());
        tokenRepository.save(token);
        deliver(organisation, user, delivery, channel, identifier, value);
        audit.logPersisted(LogLevel.INFO, LogCategory.AUTH, AuthAuditService.ORG_VERIFICATION_SENT,
                identifier, organisation.getSlug(), organisation.getId(),
                "register-required " + purpose.name() + " " + channel.name());
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    /** Delivery needs nexxbotify; when no base URL is configured the features
     * that depend on it are locked and every request is rejected up front
     * rather than failing at send time. */
    private void requireNotifier() {
        if (!nexxbotifyClient.isConfigured()) {
            throw new BadRequestException(
                    "This feature is not available because the verification service is not configured");
        }
    }

    private void requireFeature(Organisation organisation, VerificationPurpose purpose) {
        OrganisationAuthConfig config = authConfigService.configOf(organisation);
        boolean enabled = switch (purpose) {
            case EMAIL_VERIFICATION -> config.isEmailVerificationEnabled()
                    || config.isRequireEmailVerificationOnRegister();
            case PHONE_VERIFICATION -> config.isPhoneVerificationEnabled()
                    || config.isRequirePhoneVerificationOnRegister();
            case PASSWORD_RESET -> config.isPasswordResetEnabled();
            case LOGIN_OTP -> config.isOtpLoginEnabled();
            case TWO_FACTOR -> config.isTwoFactorEnabled();
        };
        if (!enabled) {
            throw new BadRequestException("This feature is not enabled for the organisation");
        }
    }

    private void validatePurposeVsChannel(VerificationPurpose purpose, VerificationChannel channel) {
        boolean mismatch = switch (purpose) {
            case EMAIL_VERIFICATION -> channel != VerificationChannel.EMAIL;
            case PHONE_VERIFICATION -> channel != VerificationChannel.SMS;
            default -> false;
        };
        if (mismatch) {
            throw new BadRequestException("The channel does not match the verification purpose");
        }
    }

    private VerificationChannel channelFor(VerificationPurpose purpose) {
        return switch (purpose) {
            case EMAIL_VERIFICATION -> VerificationChannel.EMAIL;
            case PHONE_VERIFICATION -> VerificationChannel.SMS;
            default -> throw new BadRequestException("The channel does not match the verification purpose");
        };
    }

    private String normalize(VerificationChannel channel, String identifier) {
        String normalized = switch (channel) {
            case EMAIL -> Emails.normalize(identifier);
            case SMS -> Phones.normalize(identifier);
        };
        if (normalized == null || normalized.isBlank()) {
            throw new BadRequestException(
                    "Invalid " + (channel == VerificationChannel.EMAIL ? "email" : "phone number"));
        }
        return normalized;
    }

    /** The user owning {@code identifier}, when one exists. Address
     * verification and login-OTP need the account; password reset and reset
     * codes are still delivered when no account is found (anti-enumeration),
     * in which case {@code null} is returned. */
    private OrganisationUser findUser(Organisation organisation, String identifier, VerificationPurpose purpose) {
        Optional<OrganisationUser> found = findByIdentifier(organisation, identifier,
                purpose == VerificationPurpose.EMAIL_VERIFICATION ? VerificationChannel.EMAIL
                        : purpose == VerificationPurpose.PHONE_VERIFICATION ? VerificationChannel.SMS
                        : null);
        if (purpose == VerificationPurpose.EMAIL_VERIFICATION
                || purpose == VerificationPurpose.PHONE_VERIFICATION) {
            return found.orElseThrow(() -> new BadRequestException(
                    "No account found for this " + (purpose == VerificationPurpose.EMAIL_VERIFICATION ? "email" : "phone number")));
        }
        return found.orElse(null);
    }

    private OrganisationUser findUserForPasswordReset(Organisation organisation, String identifier) {
        return findByIdentifier(organisation, identifier, null).orElse(null);
    }

    private Optional<OrganisationUser> findByIdentifier(Organisation organisation, String identifier,
                                                        VerificationChannel forcedChannel) {
        if (forcedChannel == VerificationChannel.EMAIL) {
            return userRepository.findByOrganisationIdAndEmail(organisation.getId(), identifier);
        }
        if (forcedChannel == VerificationChannel.SMS) {
            return userRepository.findByOrganisationIdAndPhone(organisation.getId(), identifier);
        }
        return userRepository.findByOrganisationIdAndEmail(organisation.getId(), identifier)
                .or(() -> userRepository.findByOrganisationIdAndPhone(organisation.getId(), identifier))
                .or(() -> userRepository.findByOrganisationIdAndUsername(organisation.getId(), identifier));
    }

    private void enforceResendInterval(OrganisationVerificationToken active, Instant now) {
        Duration wait = Duration.between(now, active.getCreatedAt().plus(properties.getResendInterval()));
        if (!wait.isNegative() && !wait.isZero()) {
            throw new BadRequestException("Please wait before requesting another code");
        }
    }

    private void recordFailedAttempt(OrganisationVerificationToken token, Instant now) {
        token.setAttempts(token.getAttempts() + 1);
        if (token.getAttempts() >= properties.getMaxAttempts()) {
            token.setConsumedAt(now);
        }
        tokenRepository.save(token);
    }

    private void markVerified(OrganisationUser user, VerificationPurpose purpose, Instant now) {
        if (purpose == VerificationPurpose.EMAIL_VERIFICATION) {
            user.setEmailVerifiedAt(now);
        } else if (purpose == VerificationPurpose.PHONE_VERIFICATION) {
            user.setPhoneVerifiedAt(now);
        }
        user.bumpDataHash();
        userRepository.save(user);
    }

    private String generate(VerificationDelivery delivery) {
        return switch (delivery) {
            case OTP -> generateOtp(properties.getOtpLength());
            case LINK -> generateToken();
        };
    }

    private String generateOtp(int length) {
        int bound = (int) Math.pow(10, length);
        int value = random.nextInt(bound);
        return String.format("%0" + length + "d", value);
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void deliver(Organisation organisation, OrganisationUser user,
                         VerificationDelivery delivery, VerificationChannel channel,
                         String identifier, String value) {
        Map<String, Object> variables = switch (delivery) {
            case OTP -> Map.of("code", value);
            case LINK -> Map.of("link", buildLink(organisation, value));
        };
        try {
            nexxbotifyClient.sendForOrganisation(organisation, delivery, channel, identifier, variables);
        } catch (IllegalStateException e) {
            throw new ServiceUnavailableException(e.getMessage());
        }
    }

    private String buildLink(Organisation organisation, String rawToken) {
        return properties.getBaseUrl() + "/" + organisation.getPlatform().getSlug()
                + "/auth/verifications/complete?token=" + rawToken;
    }

    private Organisation resolveOrganisation(String platformSlug, Long organisationId, String clientId) {
        if (clientId != null && !clientId.isBlank()) {
            OrganisationClient client = clientRepository.findByClientKey(clientId.trim())
                    .orElseThrow(() -> ResourceNotFoundException.of("Organisation client", clientId));
            Organisation organisation = client.getOrganisation();
            if (!organisation.getPlatform().getSlug().equals(platformSlug)) {
                throw ResourceNotFoundException.of("Organisation", organisation.getId());
            }
            return organisation;
        }
        if (organisationId != null) {
            Organisation organisation = organisationRepository.findById(organisationId)
                    .orElseThrow(() -> ResourceNotFoundException.of("Organisation", organisationId));
            if (!organisation.getPlatform().getSlug().equals(platformSlug)) {
                throw ResourceNotFoundException.of("Organisation", organisationId);
            }
            return organisation;
        }
        throw new BadRequestException("X-Client-Id header or organisationId is required");
    }

    private String hashToken(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** The normalized address of {@code user} on the given channel. */
    private String normalizedIdentifier(OrganisationUser user, VerificationChannel channel) {
        String raw = switch (channel) {
            case EMAIL -> user.getEmail();
            case SMS -> user.getPhone();
        };
        String normalized = normalize(channel, raw);
        return normalized;
    }

    private String signChallenge(Organisation organisation, OrganisationUser user,
                                 VerificationPurpose purpose, VerificationChannel channel,
                                 String identifier, Duration ttl) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim(CLAIM_ORG_ID, organisation.getId())
                .claim(CLAIM_CHANNEL, channel.name())
                .claim(CLAIM_PURPOSE, purpose.name())
                .claim(CLAIM_IDENTIFIER, identifier)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(challengeKey())
                .compact();
    }

    private Claims parseChallenge(String raw) {
        try {
            return Jwts.parser().verifyWith(challengeKey()).build()
                    .parseSignedClaims(raw).getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidCredentialsException();
        }
    }

    private Long orgId(Claims claims) {
        Number orgId = claims.get(CLAIM_ORG_ID, Number.class);
        return orgId == null ? null : orgId.longValue();
    }

    private SecretKey challengeKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }
}