package com.nexxserve.nexxauth.entity;

/**
 * Authentication method of an organisation user. Only {@link #PASSWORD} exists
 * today; the enum is the extension point for future modes (OTP, magic link,
 * SSO, ...). A user whose {@code authType} is {@code null} has no auth method
 * configured and cannot log in.
 */
public enum AuthType {
    PASSWORD
}
