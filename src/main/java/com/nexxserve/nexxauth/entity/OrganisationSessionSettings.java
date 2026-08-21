package com.nexxserve.nexxauth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Organisation-level session settings: how long org access and refresh tokens
 * live, and how many concurrent sessions (active refresh tokens) one org user
 * may hold. One row per organisation, created lazily with defaults that match
 * the platform's {@code app.jwt.*} values. Completely independent of the
 * platform auth flow.
 */
@Getter
@Setter
@Entity
@Table(name = "organisation_session_settings")
public class OrganisationSessionSettings extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organisation_id", nullable = false, unique = true)
    private Organisation organisation;

    /** Access-token lifetime in seconds (default 900 = 15m, matches app.jwt). */
    @Column(name = "access_token_ttl_seconds", nullable = false)
    private long accessTokenTtlSeconds = 900;

    /** Refresh-token lifetime in seconds (default 604800 = 7d, matches app.jwt). */
    @Column(name = "refresh_token_ttl_seconds", nullable = false)
    private long refreshTokenTtlSeconds = 604800;

    /** Concurrent sessions per user; the oldest session is evicted on overflow. */
    @Column(name = "max_sessions_per_user", nullable = false)
    private int maxSessionsPerUser = 5;
}
