package com.nexxserve.nexxauth.entity;

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

    /** When true, users' email addresses must be verified (a verification
     * code/link is sent via nexxbotify on request). Unverified users still
     * sign in but get the VERIFY_EMAIL action. */
    @Column(name = "email_verification_enabled", nullable = false)
    private boolean emailVerificationEnabled = false;

    /** When true, users' phone numbers must be verified (a verification
     * code/link is sent via nexxbotify on request). Unverified users still
     * sign in but get the VERIFY_PHONE action. */
    @Column(name = "phone_verification_enabled", nullable = false)
    private boolean phoneVerificationEnabled = false;

    /** When true, users may reset a forgotten password through an OTP or
     * magic link sent via nexxbotify. */
    @Column(name = "password_reset_enabled", nullable = false)
    private boolean passwordResetEnabled = false;

    /** When true, OTP login is allowed: a user signs in with a one-time code
     * sent to their identifier instead of a password. */
    @Column(name = "otp_login_enabled", nullable = false)
    private boolean otpLoginEnabled = false;

    /** When true, every password login requires a second factor: the server
     * sends a one-time code to the user's email/phone and the login only
     * completes after it is submitted on the challenge endpoint. */
    @Column(name = "two_factor_enabled", nullable = false)
    private boolean twoFactorEnabled = false;

    /** Default delivery for verification requests that do not pick one: OTP
     * (a numeric code typed back) or LINK (a magic link opened in a browser).
     * Login challenges (2FA, verify-at-next-login) are always OTP. */
    @Enumerated(EnumType.STRING)
    @Column(name = "verification_mode", nullable = false, length = 10)
    private VerificationDelivery verificationMode = VerificationDelivery.OTP;

    /** When true, new registrations may only reach full access after
     * verifying their email: the registration response carries a gating
     * VERIFY_EMAIL action and a restricted session until the email is
     * verified. */
    @Column(name = "require_email_verification_on_register", nullable = false)
    private boolean requireEmailVerificationOnRegister = false;

    /** When true, new registrations may only reach full access after verifying
     * their phone number (gating VERIFY_PHONE action until verified). */
    @Column(name = "require_phone_verification_on_register", nullable = false)
    private boolean requirePhoneVerificationOnRegister = false;
}
