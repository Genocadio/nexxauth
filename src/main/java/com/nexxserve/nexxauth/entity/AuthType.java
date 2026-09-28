package com.nexxserve.nexxauth.entity;

/**
 * Authentication method of an organisation user. {@link #PASSWORD} (BCrypt
 * password) and {@link #OTP} (one-time code to email/phone via nexxbotify) are
 * available today; the enum is the extension point for future modes (magic
 * link, SSO, ...). A user whose {@code authType} is {@code null} has no auth
 * method configured and cannot log in.
 */
public enum AuthType {
    PASSWORD,
    OTP,
    PASSWORDLESS,
    VERIFY;

    public boolean isPasswordless() {
        return this == OTP || this == PASSWORDLESS;
    }

    public boolean isVerify() {
        return this == VERIFY;
    }
}
