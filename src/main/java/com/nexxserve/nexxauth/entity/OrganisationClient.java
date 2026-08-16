package com.nexxserve.nexxauth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * An external app talking to an organisation's API. Identified on requests via
 * an opaque, non-guessable {@link #getClientKey()} sent as the {@code X-Client-Id}
 * header; auth-required clients additionally present
 * {@code Authorization: Bearer <static token>} (stored only as a SHA-256 hash).
 * See {@link ClientType} for the per-type access rules.
 */
@Getter
@Setter
@Entity
@Table(name = "organisation_clients")
public class OrganisationClient extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organisation_id", nullable = false)
    private Organisation organisation;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** Opaque, non-guessable identifier apps send as {@code X-Client-Id}. */
    @Column(name = "client_key", length = 64)
    private String clientKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "client_type", nullable = false, length = 20)
    private ClientType type;

    @Column(name = "require_authentication", nullable = false)
    private boolean requireAuthentication;

    /** SHA-256 hex of the client's static token; never the token itself. */
    @Column(name = "token_hash", length = 64)
    private String tokenHash;

    /** Comma-separated trusted origins for CORS (may be blank). */
    @Column(name = "allowed_origins", length = 2000)
    private String allowedOrigins;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    /** Free-form JSON settings (may be blank). */
    @Column(name = "settings", length = 4000)
    private String settings;
}
