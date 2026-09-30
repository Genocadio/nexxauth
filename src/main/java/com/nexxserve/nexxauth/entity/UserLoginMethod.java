package com.nexxserve.nexxauth.entity;

/**
 * How a single organisation user is allowed to authenticate, chosen per user by
 * an administrator. This is deliberately separate from the organisation-wide
 * {@link AuthType} on {@code organisation_auth_config} and from the
 * {@code authType} stamped on the user when a password is set: the org config
 * decides the default, this decides what the user may actually use.
 *
 * <p>It is the field that makes "remove the password but keep the user able to
 * sign in" expressible — clearing the password alone leaves a user with no
 * method at all.
 *
 * <p>{@link #PASSWORD_OR_OTP} is the default so that introducing this field
 * changes nothing for an existing account: before it existed, any user with a
 * password could also sign in with a one-time code. {@link #PASSWORD} and
 * {@link #OTP} are the opt-in restrictions.
 */
public enum UserLoginMethod {
    /** Password only. A one-time code is refused for this user. */
    PASSWORD,
    /** One-time codes only. A password is never accepted for this user. */
    OTP,
    /** Either a password or a one-time code. The default. */
    PASSWORD_OR_OTP;

    /** True when a password is an acceptable way to sign in. */
    public boolean allowsPassword() {
        return this != OTP;
    }

    /** True when a one-time code is an acceptable way to sign in. */
    public boolean allowsOtp() {
        return this != PASSWORD;
    }
}
