package com.nexxserve.nexxauth.service;

import com.nexxserve.nexxauth.exception.RefreshTokenException;
import com.nexxserve.nexxauth.security.JwtProperties;
import com.nexxserve.nexxauth.util.RefreshTokens;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Generic rotating refresh-token flow shared by platform users and organisation
 * users so the security-critical logic (rotation, reuse detection, family
 * revocation) is defined exactly once. Subclasses supply the persistence hooks
 * for their entity/repository.
 *
 * @param <S> the subject type (platform user / organisation user)
 * @param <T> the stored token entity type
 */
public abstract class AbstractRefreshTokenService<S, T> {

    protected final JwtProperties jwtProperties;
    /** Runs the token-family revocation in its own transaction so it survives
     * the rollback of the (rejected) refresh request that detected the reuse. */
    private final TransactionTemplate familyRevocation;

    protected AbstractRefreshTokenService(JwtProperties jwtProperties,
                                          PlatformTransactionManager transactionManager) {
        this.jwtProperties = jwtProperties;
        this.familyRevocation = new TransactionTemplate(transactionManager);
        this.familyRevocation.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Issues a refresh token with the platform default lifetime. */
    @Transactional
    public String issue(S subject) {
        return issue(subject, jwtProperties.refreshTokenTtl());
    }

    /** Issues a refresh token with an explicit lifetime (per-org settings). */
    @Transactional
    public String issue(S subject, Duration ttl) {
        String rawToken = RefreshTokens.generateRaw();
        save(createToken(subject, RefreshTokens.hash(rawToken), Instant.now().plus(ttl)));
        return rawToken;
    }

    /** Validates the presented token and rotates it, returning the new raw token
     * (platform default lifetime). */
    @Transactional
    public String rotate(String rawToken) {
        return rotate(rawToken, jwtProperties.refreshTokenTtl());
    }

    /** Validates the presented token and rotates it with an explicit lifetime. */
    @Transactional
    public String rotate(String rawToken, Duration ttl) {
        return rotateWithSubject(rawToken, ttl).newToken();
    }

    /** Rotates the presented token with the platform default lifetime and
     * returns both the fresh raw token and the subject that owns it in a single
     * lookup. */
    @Transactional
    public Rotation<S> rotateWithSubject(String rawToken) {
        return rotateWithSubject(rawToken, jwtProperties.refreshTokenTtl());
    }

    /** {@link #rotateWithSubject(String)} with an explicit lifetime. */
    @Transactional
    public Rotation<S> rotateWithSubject(String rawToken, Duration ttl) {
        return rotateWithSubject(rawToken, ignored -> ttl);
    }

    /** {@link #rotateWithSubject(String, Duration)} variant that derives the
     * lifetime from the subject - needed when the TTL depends on the subject
     * (per-organisation settings) and is only known after the single lookup. */
    @Transactional
    public Rotation<S> rotateWithSubject(String rawToken, java.util.function.Function<S, Duration> ttl) {
        T current = findUsable(rawToken);
        S subject = subjectOf(current);
        if (!subjectEnabled(subject)) {
            throw new RefreshTokenException("User account is disabled");
        }
        markRevoked(current);
        return new Rotation<>(subject, issue(subject, ttl.apply(subject)));
    }

    /** A rotated refresh token together with the subject it belongs to. */
    public record Rotation<S>(S subject, String newToken) {
    }

    @Transactional
    public void revoke(String rawToken) {
        findByTokenHash(RefreshTokens.hash(rawToken)).ifPresent(token -> {
            if (!isRevoked(token)) {
                markRevoked(token);
            }
        });
    }

    /** Revokes every outstanding refresh token of a subject (password change,
     * account disable, detected token reuse). */
    @Transactional
    public void revokeAllForUser(Long subjectId) {
        revokeAllForSubject(subjectId, Instant.now());
    }

    @Transactional
    public S resolveSubject(String rawToken) {
        return subjectOf(findUsable(rawToken));
    }

    /** Returns the subject owning the presented token for best-effort
     * attribution (e.g. audit on logout). Never throws and has no revocation
     * side effects: unlike {@link #findUsable} it does not treat a presented
     * revoked token as theft, and unlike {@link #resolveSubject} it tolerates
     * unknown/expired tokens. Call before {@link #revoke} - a revoked token
     * no longer resolves here. */
    @Transactional(readOnly = true)
    public java.util.Optional<S> findSubjectForAudit(String rawToken) {
        return findByTokenHash(RefreshTokens.hash(rawToken))
                .filter(token -> !isRevoked(token) && !isExpired(token))
                .map(this::subjectOf);
    }

    /** Deletes expired/revoked tokens. Runs inside a transaction; scheduled by
     * {@link TokenMaintenanceService}. */
    @Transactional
    public void cleanupExpired() {
        deleteExpiredOrRevoked(Instant.now());
    }

    private T findUsable(String rawToken) {
        T token = findByTokenHash(RefreshTokens.hash(rawToken))
                .orElseThrow(() -> new RefreshTokenException("Invalid refresh token"));
        if (isEvicted(token)) {
            // Evicted by the session limit: just invalid - deliberately NOT the
            // theft path, so a stale evicted token cannot kill newer sessions.
            throw new RefreshTokenException("Refresh token has been revoked");
        }
        if (isRevoked(token)) {
            // A rotated token must never be presented again: treat it as theft
            // and revoke every token of the subject (the whole token family).
            S subject = subjectOf(token);
            Long subjectId = subjectIdOf(subject);
            familyRevocation.executeWithoutResult(status -> revokeAllForSubject(subjectId, Instant.now()));
            onReuseDetected(subject);
            throw new RefreshTokenException("Refresh token has been revoked");
        }
        if (isExpired(token)) {
            throw new RefreshTokenException("Refresh token has expired");
        }
        return token;
    }

    // --- persistence hooks (implemented by the concrete service) ---

    protected abstract T createToken(S subject, String tokenHash, Instant expiresAt);

    protected abstract void save(T token);

    protected abstract Optional<T> findByTokenHash(String tokenHash);

    protected abstract S subjectOf(T token);

    protected abstract boolean subjectEnabled(S subject);

    protected abstract Long subjectIdOf(S subject);

    protected abstract boolean isRevoked(T token);

    /** Whether the token was evicted by a session limit. Default: never.
     * Unlike {@link #isRevoked}, an evicted token is invalid without triggering
     * the family-wide theft detection. */
    protected boolean isEvicted(T token) {
        return false;
    }

    protected abstract boolean isExpired(T token);

    protected abstract void markRevoked(T token);

    protected abstract void revokeAllForSubject(Long subjectId, Instant now);

    protected abstract void deleteExpiredOrRevoked(Instant now);

    /** Hook for subclasses to record a detected token reuse (theft). Default:
     * no audit. Runs inside the request that presented the stolen token. */
    protected void onReuseDetected(S subject) {
    }
}
