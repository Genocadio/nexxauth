package com.nexxserve.nexxauth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Rotating refresh token for an organisation user. Only the SHA-256 hash of the
 * raw token is persisted; each refresh rotates the token so a stolen token is
 * usable at most once.
 */
@Getter
@Setter
@Entity
@Table(name = "organisation_refresh_tokens")
public class OrganisationRefreshToken extends BaseEntity {

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organisation_user_id", nullable = false)
    private OrganisationUser organisationUser;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    /** Set when the session was evicted by the concurrent-session limit.
     * Distinct from {@link #revokedAt}: an evicted token is just invalid - it
     * must NOT trigger the family-wide theft detection of a replayed token. */
    @Column(name = "evicted_at")
    private Instant evictedAt;

    public boolean isExpired() {
        return expiresAt.isBefore(Instant.now());
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isEvicted() {
        return evictedAt != null;
    }
}
