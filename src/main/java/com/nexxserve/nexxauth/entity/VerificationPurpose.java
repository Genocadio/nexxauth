package com.nexxserve.nexxauth.entity;

/**
 * What a verification flow is for. Each purpose maps to a per-organisation
 * toggle in {@link OrganisationAuthConfig} and, after successful verification,
 * to either a user profile flag (email/phone) or a login/reset credential.
 */
public enum VerificationPurpose {

    /** Prove ownership of an email address; sets {@code emailVerifiedAt}. */
    EMAIL_VERIFICATION,

    /** Prove ownership of a phone number; sets {@code phoneVerifiedAt}. */
    PHONE_VERIFICATION,

    /** Establish a new password without knowing the current one. */
    PASSWORD_RESET,

    /** One-time password to sign in (OTP login flow). */
    LOGIN_OTP,

    /** Second factor after a password login (2FA, or a forced "verify at next
     * login"). The server sends the OTP and the login only completes after it
     * is submitted back on the challenge endpoint. */
    TWO_FACTOR
}