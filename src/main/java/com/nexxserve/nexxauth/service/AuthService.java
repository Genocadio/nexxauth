package com.nexxserve.nexxauth.service;

import com.nexxserve.nexxauth.dto.request.ChangePasswordRequest;
import com.nexxserve.nexxauth.dto.request.LoginRequest;
import com.nexxserve.nexxauth.dto.request.RegisterRequest;
import com.nexxserve.nexxauth.dto.request.UpdateProfileRequest;
import com.nexxserve.nexxauth.dto.response.AuthResponse;
import com.nexxserve.nexxauth.dto.response.PlatformUserResponse;
import com.nexxserve.nexxauth.entity.PlatformUser;
import com.nexxserve.nexxauth.exception.ConflictException;
import com.nexxserve.nexxauth.exception.InvalidCredentialsException;
import com.nexxserve.nexxauth.exception.ResourceNotFoundException;
import com.nexxserve.nexxauth.mapper.PlatformUserMapper;
import com.nexxserve.nexxauth.repository.PlatformUserRepository;
import com.nexxserve.nexxauth.security.AuthTiming;
import com.nexxserve.nexxauth.security.AuthenticatedUser;
import com.nexxserve.nexxauth.security.JwtProperties;
import com.nexxserve.nexxauth.security.JwtService;
import com.nexxserve.nexxauth.util.Emails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final PlatformUserRepository platformUserRepository;
    private final PlatformService platformService;
    private final RefreshTokenService refreshTokenService;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final PasswordEncoder passwordEncoder;
    private final PlatformUserMapper platformUserMapper;
    private final AuthAuditService audit;
    private final AuthTiming authTiming;

    public AuthService(PlatformUserRepository platformUserRepository, PlatformService platformService,
                       RefreshTokenService refreshTokenService, JwtService jwtService, JwtProperties jwtProperties,
                       PasswordEncoder passwordEncoder, PlatformUserMapper platformUserMapper,
                       AuthAuditService audit, AuthTiming authTiming) {
        this.platformUserRepository = platformUserRepository;
        this.platformService = platformService;
        this.refreshTokenService = refreshTokenService;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.passwordEncoder = passwordEncoder;
        this.platformUserMapper = platformUserMapper;
        this.audit = audit;
        this.authTiming = authTiming;
    }

    /**
     * Signup: creates a new platform and registers the caller as its super user.
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = Emails.normalize(request.email());
        if (platformUserRepository.existsByEmail(email)) {
            throw new ConflictException("A user with email " + email + " already exists");
        }
        PlatformUser user = platformUserMapper.toEntity(request);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.password()));
        PlatformUser saved = platformService.createPlatformWithOwner(
                request.platformName(), request.platformSlug(), user);
        audit.log(AuthAuditService.PLATFORM_REGISTER, saved.getEmail(), saved.getPlatform().getSlug());
        return issueTokens(saved);
    }

    /**
     * Not read-only: login persists a fresh refresh token.
     */
    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = Emails.normalize(request.email());
        PlatformUser user = platformUserRepository.findByEmail(email)
                .orElseThrow(() -> {
                    audit.log(AuthAuditService.PLATFORM_LOGIN_FAILURE, email, null);
                    // Burn the same BCrypt cost as a real check so unknown and
                    // known accounts answer in the same time.
                    authTiming.equalsUnknown(request.password());
                    return new InvalidCredentialsException();
                });
        boolean passwordMatches = passwordEncoder.matches(request.password(), user.getPassword());
        if (!user.isEnabled() || !passwordMatches) {
            audit.log(AuthAuditService.PLATFORM_LOGIN_FAILURE, email, user.getPlatform().getSlug());
            if (!user.isEnabled()) {
                audit.log(AuthAuditService.PLATFORM_DISABLED, email, user.getPlatform().getSlug());
            }
            throw new InvalidCredentialsException();
        }
        audit.log(AuthAuditService.PLATFORM_LOGIN_SUCCESS, email, user.getPlatform().getSlug());
        return issueTokens(user);
    }

    /**
     * Rotates the refresh token and issues a fresh access token. The user is
     * resolved before rotation since rotation revokes the presented token.
     */
    @Transactional
    public AuthResponse refresh(String rawRefreshToken) {
        var rotation = refreshTokenService.rotateWithSubject(rawRefreshToken);
        audit.log(AuthAuditService.PLATFORM_REFRESH,
                rotation.subject().getEmail(), rotation.subject().getPlatform().getSlug());
        return issueTokens(rotation.subject(), rotation.newToken());
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        // Attribute first (revoking would make the token unresolvable), then
        // revoke. Idempotent: an unknown/already-revoked token still logs out
        // with a 204 and simply has no audit actor.
        refreshTokenService.findSubjectForAudit(rawRefreshToken).ifPresent(user ->
                audit.log(AuthAuditService.PLATFORM_LOGOUT, user.getEmail(), user.getPlatform().getSlug()));
        refreshTokenService.revoke(rawRefreshToken);
    }

    @Transactional(readOnly = true)
    public PlatformUserResponse me(AuthenticatedUser requester) {
        PlatformUser user = findById(requester.id());
        return platformUserMapper.toResponse(user);
    }

    @Transactional
    public PlatformUserResponse updateProfile(AuthenticatedUser requester, UpdateProfileRequest request) {
        PlatformUser user = findById(requester.id());
        if (request.firstName() != null) {
            user.setFirstName(request.firstName());
        }
        if (request.lastName() != null) {
            user.setLastName(request.lastName());
        }
        if (request.phone() != null) {
            user.setPhone(request.phone());
        }
        return platformUserMapper.toResponse(platformUserRepository.save(user));
    }

    @Transactional
    public void changePassword(AuthenticatedUser requester, ChangePasswordRequest request) {
        PlatformUser user = findById(requester.id());
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new InvalidCredentialsException();
        }
        // Force re-authentication everywhere: revoke every outstanding refresh
        // token first, then update the password so the revoked-session state and
        // the new password are both persisted.
        refreshTokenService.revokeAllForUser(user.getId());
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        platformUserRepository.save(user);
        audit.log(AuthAuditService.PLATFORM_PASSWORD_CHANGED, user.getEmail(), user.getPlatform().getSlug());
    }

    private AuthResponse issueTokens(PlatformUser user) {
        return issueTokens(user, refreshTokenService.issue(user));
    }

    private AuthResponse issueTokens(PlatformUser user, String refreshToken) {
        String accessToken = jwtService.generateAccessToken(user);
        PlatformUserResponse userResponse = platformUserMapper.toResponse(user);
        return AuthResponse.of(accessToken, refreshToken,
                jwtProperties.accessTokenTtl().toSeconds(), userResponse);
    }

    private PlatformUser findById(Long id) {
        return platformUserRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("User", id));
    }
}
