package com.nexxserve.nexxauth.service;

import com.nexxserve.nexxauth.entity.PlatformUser;
import com.nexxserve.nexxauth.entity.RefreshToken;
import com.nexxserve.nexxauth.repository.RefreshTokenRepository;
import com.nexxserve.nexxauth.security.JwtProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Instant;
import java.util.Optional;

/**
 * Issues, rotates and revokes opaque refresh tokens for platform users. Only
 * SHA-256 hashes are stored; each refresh rotates the token (old one revoked)
 * so a stolen token is usable at most once. Rotation/reuse-detection logic
 * lives in {@link AbstractRefreshTokenService}.
 */
@Service
public class RefreshTokenService extends AbstractRefreshTokenService<PlatformUser, RefreshToken> {

    private final RefreshTokenRepository refreshTokenRepository;
    private final AuthAuditService audit;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, JwtProperties jwtProperties,
                               PlatformTransactionManager transactionManager, AuthAuditService audit) {
        super(jwtProperties, transactionManager);
        this.refreshTokenRepository = refreshTokenRepository;
        this.audit = audit;
    }

    @Override
    protected RefreshToken createToken(PlatformUser subject, String tokenHash, Instant expiresAt) {
        RefreshToken token = new RefreshToken();
        token.setTokenHash(tokenHash);
        token.setPlatformUser(subject);
        token.setExpiresAt(expiresAt);
        return token;
    }

    @Override
    protected void save(RefreshToken token) {
        refreshTokenRepository.save(token);
    }

    @Override
    protected Optional<RefreshToken> findByTokenHash(String tokenHash) {
        return refreshTokenRepository.findByTokenHash(tokenHash);
    }

    @Override
    protected PlatformUser subjectOf(RefreshToken token) {
        return token.getPlatformUser();
    }

    @Override
    protected boolean subjectEnabled(PlatformUser subject) {
        return subject.isEnabled();
    }

    @Override
    protected Long subjectIdOf(PlatformUser subject) {
        return subject.getId();
    }

    @Override
    protected boolean isRevoked(RefreshToken token) {
        return token.isRevoked();
    }

    @Override
    protected boolean isExpired(RefreshToken token) {
        return token.isExpired();
    }

    @Override
    protected void markRevoked(RefreshToken token) {
        token.setRevokedAt(Instant.now());
    }

    @Override
    protected void revokeAllForSubject(Long subjectId, Instant now) {
        refreshTokenRepository.revokeAllForUser(subjectId, now);
    }

    @Override
    protected void deleteExpiredOrRevoked(Instant now) {
        refreshTokenRepository.deleteExpiredOrRevoked(now);
    }

    @Override
    protected void onReuseDetected(PlatformUser subject) {
        audit.log(AuthAuditService.PLATFORM_TOKEN_REUSE, subject.getEmail(), subject.getPlatform().getSlug());
    }
}
