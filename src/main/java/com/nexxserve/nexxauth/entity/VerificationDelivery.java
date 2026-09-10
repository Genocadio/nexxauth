package com.nexxserve.nexxauth.entity;

/**
 * How a verification value is delivered: a short numeric {@link #OTP} the user
 * types back in, or a {@link #LINK} (magic link) the user opens. Both are
 * single-use, time-boxed, and stored hashed.
 */
public enum VerificationDelivery {
    OTP,
    LINK
}