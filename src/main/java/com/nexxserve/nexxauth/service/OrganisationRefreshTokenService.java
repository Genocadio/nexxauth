package com.nexxserve.nexxauth.service;

import com.nexxserve.nexxauth.entity.Organisation;
import com.nexxserve.nexxauth.entity.OrganisationRefreshToken;
import com.nexxserve.nexxauth.entity.OrganisationUser;
import com.nexxserve.nexxauth.repository.OrganisationRefreshTokenRepository;
import com.nexxserve.nexxauth.security.JwtProperties;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Issues, rotates and revokes opaque refresh tokens for organisation users
 * (same security model as {@link RefreshTokenService}: hashed storage, rotation
 * on every refresh, token-family revocation on detected reuse). Also enforces
 * the organisation's concurrent-session limit: when a user exceeds the allowed
 * number of live sessions the oldest session is evicted.
 */
@Service
public class OrganisationRefreshTokenService
        extends AbstractRefreshTokenService<OrganisationUser, OrganisationRefreshToken> {

    private final OrganisationRefreshTokenRepository refreshTokenRepository;
    private final OrganisationSessionSettingsService sessionSettingsService;
    private final EntityManager entityManager;
    private final AuthAuditService audit;

    public OrganisationRefreshTokenService(OrganisationRefreshTokenRepository refreshTokenRepository,
                                           JwtProperties jwtProperties, PlatformTransactionManager transactionManager,
                                           OrganisationSessionSettingsService sessionSettingsService,
                                           EntityManager entityManager,
                                           AuthAuditService audit) {
        super(jwtProperties, transactionManager);
        this.refreshTokenRepository = refreshTokenRepository;
        this.sessionSettingsService = sessionSettingsService;
        this.entityManager = entityManager;
        this.audit = audit;
    }

    /**
     * Ensures the user stays within the org's concurrent-session limit. Call
     * before issuing a new session: if the user is at (or over) the limit, the
     * oldest live sessions are evicted so the new one fits. A session limit of
     * 1 makes every new login sign out all previous sessions. Eviction marks
     * the token {@code evictedAt} (not {@code revokedAt}): the evicted session
     * dies quietly and cannot trigger the family-wide theft detection.
     */
    @Transactional
    public void enforceSessionLimit(Organisation organisation, Long userId) {
        // Serialize concurrent session issuance for the user's organisation on
        // its row: otherwise two parallel logins could both count the same set
        // of active sessions and together exceed the limit. (The user row is
        // already managed in this transaction, so the org row - always present,
        // lock-reentrant across the settings lookup below - is the safe thing
        // to lock.)
        entityManager.lock(entityManager.merge(organisation), LockModeType.PESSIMISTIC_WRITE);
        int max = sessionSettingsService.maxSessionsPerUser(organisation);
        List<OrganisationRefreshToken> active =
                refreshTokenRepository.findActiveByUserIdOrderByExpiresAtAsc(userId, Instant.now());
        // +1: a new session is about to be issued after this call.
        int excess = active.size() - max + 1;
        for (int i = 0; i < excess; i++) {
            active.get(i).setEvictedAt(Instant.now());
        }
    }

    @Override
    protected OrganisationRefreshToken createToken(OrganisationUser subject, String tokenHash, Instant expiresAt) {
        OrganisationRefreshToken token = new OrganisationRefreshToken();
        token.setTokenHash(tokenHash);
        token.setOrganisationUser(subject);
        token.setExpiresAt(expiresAt);
        return token;
    }

    @Override
    protected void save(OrganisationRefreshToken token) {
        refreshTokenRepository.save(token);
    }

    @Override
    protected Optional<OrganisationRefreshToken> findByTokenHash(String tokenHash) {
        return refreshTokenRepository.findByTokenHash(tokenHash);
    }

    @Override
    protected OrganisationUser subjectOf(OrganisationRefreshToken token) {
        return token.getOrganisationUser();
    }

    @Override
    protected boolean subjectEnabled(OrganisationUser subject) {
        return subject.isEnabled();
    }

    @Override
    protected Long subjectIdOf(OrganisationUser subject) {
        return subject.getId();
    }

    @Override
    protected boolean isRevoked(OrganisationRefreshToken token) {
        return token.isRevoked();
    }

    @Override
    protected boolean isEvicted(OrganisationRefreshToken token) {
        return token.isEvicted();
    }

    @Override
    protected boolean isExpired(OrganisationRefreshToken token) {
        return token.isExpired();
    }

    @Override
    protected void markRevoked(OrganisationRefreshToken token) {
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
    protected void onReuseDetected(OrganisationUser subject) {
        audit.log(AuthAuditService.ORG_TOKEN_REUSE,
                identifierOf(subject), subject.getOrganisation().getSlug());
    }

    private String identifierOf(OrganisationUser user) {
        // First non-null identifier, so phone-only users stay attributable.
        return user.getUsername() != null ? user.getUsername()
                : user.getEmail() != null ? user.getEmail()
                : user.getPhone() != null ? user.getPhone() : "unknown";
    }
}
