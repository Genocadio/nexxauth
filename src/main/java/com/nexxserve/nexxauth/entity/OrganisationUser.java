package com.nexxserve.nexxauth.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A user within an organisation. Purely managed data - no authentication. The
 * same person may exist in several organisations (one row per organisation),
 * and never outside one. Username/email/phone are optional identifiers,
 * unique per organisation, each required or login-enabled per the
 * organisation's sign-in identifier configuration.
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

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "username", length = 100)
    private String username;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("isPrimary DESC, id ASC")
    private Set<OrganisationUserEmail> emails = new HashSet<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("isPrimary DESC, id ASC")
    private Set<OrganisationUserPhone> phones = new HashSet<>();

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

    /** Which credentials this user may sign in with. Chosen per user by an
     * administrator; independent of {@link #authType}, which only records the
     * method stamped when the password was set. Never null; the default
     * accepts either credential so existing accounts are unaffected. */
    @Enumerated(EnumType.STRING)
    @Column(name = "login_method", length = 30, nullable = false)
    private UserLoginMethod loginMethod = UserLoginMethod.PASSWORD_OR_OTP;

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

    /** Opaque token that changes every time user data (profile, roles,
     * metadata, enabled) is mutated. Included in the JWT so external APIs
     * can detect stale data without hitting the database. Never exposed in
     * JSON responses — set via {@link #bumpDataHash()}. */
    @com.fasterxml.jackson.annotation.JsonIgnore
    @Column(name = "data_hash", nullable = false, length = 36)
    private String dataHash;

    /** When true (set by an admin), the user's next password login does not
     * complete until their email address is verified: the server sends a
     * one-time code and the login resumes on the challenge endpoint, which
     * then stamps the email as verified. Cleared when verified. */
    @Column(name = "require_email_verification_at_next_login", nullable = false)
    private boolean requireEmailVerificationAtNextLogin = false;

    /** When true, the user's next password login does not complete until their
     * phone number is verified (same challenge flow; cleared when verified). */
    @Column(name = "require_phone_verification_at_next_login", nullable = false)
    private boolean requirePhoneVerificationAtNextLogin = false;

    @jakarta.persistence.PrePersist
    void prePersist() {
        if (dataHash == null) dataHash = UUID.randomUUID().toString();
    }

    /** Regenerate the data hash. Call after every non-password mutation
     * (profile, roles, metadata, enabled). Password changes must NOT call
     * this — the hash represents user *data*, not credentials. */
    public void bumpDataHash() {
        this.dataHash = UUID.randomUUID().toString();
    }

    public String getPrimaryEmail() {
        return emails.stream()
                .filter(OrganisationUserEmail::isPrimary)
                .findFirst()
                .map(OrganisationUserEmail::getEmail)
                .orElseGet(() -> emails.stream().findFirst().map(OrganisationUserEmail::getEmail).orElse(null));
    }

    public String getPrimaryPhone() {
        return phones.stream()
                .filter(OrganisationUserPhone::isPrimary)
                .findFirst()
                .map(OrganisationUserPhone::getPhone)
                .orElseGet(() -> phones.stream().findFirst().map(OrganisationUserPhone::getPhone).orElse(null));
    }

    public boolean isEmailVerified() {
        return emails.stream()
                .filter(OrganisationUserEmail::isPrimary)
                .findFirst()
                .map(OrganisationUserEmail::isVerified)
                .orElseGet(() -> emails.stream().findFirst().map(OrganisationUserEmail::isVerified).orElse(false));
    }

    public boolean isPhoneVerified() {
        return phones.stream()
                .filter(OrganisationUserPhone::isPrimary)
                .findFirst()
                .map(OrganisationUserPhone::isVerified)
                .orElseGet(() -> phones.stream().findFirst().map(OrganisationUserPhone::isVerified).orElse(false));
    }

    public Instant getEmailVerifiedAt() {
        return emails.stream()
                .filter(OrganisationUserEmail::isPrimary)
                .findFirst()
                .map(OrganisationUserEmail::getVerifiedAt)
                .orElseGet(() -> emails.stream().findFirst().map(OrganisationUserEmail::getVerifiedAt).orElse(null));
    }

    public Instant getPhoneVerifiedAt() {
        return phones.stream()
                .filter(OrganisationUserPhone::isPrimary)
                .findFirst()
                .map(OrganisationUserPhone::getVerifiedAt)
                .orElseGet(() -> phones.stream().findFirst().map(OrganisationUserPhone::getVerifiedAt).orElse(null));
    }

    public OrganisationUserEmail addEmail(String emailAddress, boolean isPrimary, Instant verifiedAt) {
        if (emailAddress == null || emailAddress.isBlank()) return null;
        String normalized = emailAddress.trim().toLowerCase();
        for (OrganisationUserEmail existing : emails) {
            if (existing.getEmail().equalsIgnoreCase(normalized)) {
                if (isPrimary) {
                    emails.forEach(e -> e.setPrimary(false));
                    existing.setPrimary(true);
                }
                if (verifiedAt != null) existing.setVerifiedAt(verifiedAt);
                return existing;
            }
        }
        if (isPrimary || emails.isEmpty()) {
            emails.forEach(e -> e.setPrimary(false));
            isPrimary = true;
        }
        OrganisationUserEmail item = new OrganisationUserEmail();
        item.setOrganisation(this.organisation);
        item.setUser(this);
        item.setEmail(normalized);
        item.setPrimary(isPrimary);
        item.setVerifiedAt(verifiedAt);
        emails.add(item);
        return item;
    }

    public OrganisationUserPhone addPhone(String phoneNumber, boolean isPrimary, Instant verifiedAt) {
        if (phoneNumber == null || phoneNumber.isBlank()) return null;
        String normalized = phoneNumber.trim();
        for (OrganisationUserPhone existing : phones) {
            if (existing.getPhone().equals(normalized)) {
                if (isPrimary) {
                    phones.forEach(p -> p.setPrimary(false));
                    existing.setPrimary(true);
                }
                if (verifiedAt != null) existing.setVerifiedAt(verifiedAt);
                return existing;
            }
        }
        if (isPrimary || phones.isEmpty()) {
            phones.forEach(p -> p.setPrimary(false));
            isPrimary = true;
        }
        OrganisationUserPhone item = new OrganisationUserPhone();
        item.setOrganisation(this.organisation);
        item.setUser(this);
        item.setPhone(normalized);
        item.setPrimary(isPrimary);
        item.setVerifiedAt(verifiedAt);
        phones.add(item);
        return item;
    }

    public void removeEmail(String emailAddress) {
        if (emailAddress == null) return;
        emails.removeIf(e -> e.getEmail().equalsIgnoreCase(emailAddress.trim()));
        if (!emails.isEmpty() && emails.stream().noneMatch(OrganisationUserEmail::isPrimary)) {
            emails.iterator().next().setPrimary(true);
        }
    }

    public void removePhone(String phoneNumber) {
        if (phoneNumber == null) return;
        phones.removeIf(p -> p.getPhone().equals(phoneNumber.trim()));
        if (!phones.isEmpty() && phones.stream().noneMatch(OrganisationUserPhone::isPrimary)) {
            phones.iterator().next().setPrimary(true);
        }
    }
}
