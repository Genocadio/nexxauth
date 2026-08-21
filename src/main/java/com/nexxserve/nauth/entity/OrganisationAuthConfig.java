package com.nexxserve.nauth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Organisation-level authentication settings: the auth type new users get by
 * default and the password rules their passwords must satisfy. One row per
 * organisation; completely independent of the platform auth flow.
 */
@Getter
@Setter
@Entity
@Table(name = "organisation_auth_configs")
public class OrganisationAuthConfig extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organisation_id", nullable = false, unique = true)
    private Organisation organisation;

    /** The auth method users of this organisation use. */
    @Enumerated(EnumType.STRING)
    @Column(name = "auth_type", nullable = false, length = 30)
    private AuthType authType = AuthType.PASSWORD;

    /** When false, password authentication is disabled for the whole
     * organisation (users cannot sign in until another method is enabled). */
    @Column(name = "password_enabled", nullable = false)
    private boolean passwordEnabled = true;

    @Column(name = "password_min_length", nullable = false)
    private int passwordMinLength = 8;

    @Column(name = "password_max_length", nullable = false)
    private int passwordMaxLength = 72;

    /** Days a password stays valid; {@code 0} = never expires. */
    @Column(name = "password_expiration_days", nullable = false)
    private int passwordExpirationDays = 0;

    /** How many previous passwords a user may not reuse; {@code 0} = history disabled. */
    @Column(name = "password_history_count", nullable = false)
    private int passwordHistoryCount = 0;
}
