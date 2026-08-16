package com.nexxserve.nexxauth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

/**
 * A user within an organisation. Purely managed data - no authentication. The
 * same person may exist in several organisations (one row per organisation),
 * and never outside one. Username/email are optional identifiers, unique per
 * organisation; when the organisation has {@code useEmailAsUsername} enabled,
 * email is the required identifier.
 */
@Getter
@Setter
@Entity
@Table(name = "organisation_users")
public class OrganisationUser extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organisation_id", nullable = false)
    private Organisation organisation;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "username", length = 100)
    private String username;

    @Column(name = "email", length = 255)
    private String email;

    /** BCrypt hash; null for users created as managed data without auth. */
    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    /** The user's auth method; null = no auth configured, cannot log in. */
    @Enumerated(EnumType.STRING)
    @Column(name = "auth_type", length = 30)
    private AuthType authType;

    /** When the current password was set; used for expiry policies. */
    @Column(name = "password_changed_at")
    private java.time.Instant passwordChangedAt;

    /** True while the password is temporary (set by a platform user or forced
     * via the admin API): the user must change it at next login, which is
     * surfaced as the CHANGE_PASSWORD action and gates the session (fixed
     * 5-minute access token, no refresh token, only the change-password
     * endpoint reachable). Cleared when the user changes their own password. */
    @Column(name = "temporary_password", nullable = false)
    private boolean temporaryPassword = false;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "organisation_user_roles",
            joinColumns = @JoinColumn(name = "organisation_user_id"),
            inverseJoinColumns = @JoinColumn(name = "organisation_role_id"))
    private Set<OrganisationRole> roles = new HashSet<>();
}
