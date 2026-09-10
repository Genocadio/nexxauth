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

import java.time.Instant;

/**
 * A single-use verification value (numeric OTP or magic-link token) delivered
 * to an email address or phone number through {@code nexxbotify}. Only the
 * SHA-256 hash of the value is stored, never the raw code/link. The raw value
 * is one of: a numeric OTP (returned nowhere - sent to the user) or an opaque
 * token embedded in a magic link (the client learns it only by reading the
 * URL in the received message).
 */
@Getter
@Setter
@Entity
@Table(name = "organisation_verification_tokens")
public class OrganisationVerificationToken extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organisation_id", nullable = false)
    private Organisation organisation;

    /** The user this verification belongs to; null for password-reset and
     * OTP-login flows where the identifier is the only known key. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organisation_user_id")
    private OrganisationUser organisationUser;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 30)
    private VerificationPurpose purpose;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 10)
    private VerificationChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery", nullable = false, length = 10)
    private VerificationDelivery delivery;

    /** The normalized address the value was sent to (email or phone). */
    @Column(name = "identifier", nullable = false, length = 255)
    private String identifier;

    /** Base64 SHA-256 digest of the raw code/token. */
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    /** Failed verification attempts so far. */
    @Column(name = "attempts", nullable = false)
    private int attempts = 0;

    /** When the raw value was successfully handed to nexxbotify; used for the
     * per-identifier resend throttle. */
    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    public boolean isConsumed() {
        return consumedAt != null;
    }

    public boolean isExpired(Instant now) {
        return expiresAt.isBefore(now);
    }
}