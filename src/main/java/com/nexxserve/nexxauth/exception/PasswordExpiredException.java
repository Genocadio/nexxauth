package com.nexxserve.nexxauth.exception;

import org.springframework.http.HttpStatus;

/**
 * The org user's password is valid but has passed the organisation's password
 * expiration window and must be changed before they can log in again.
 */
public class PasswordExpiredException extends ApiException {

    public PasswordExpiredException() {
        super(HttpStatus.UNAUTHORIZED, "Password has expired. Please change it before logging in");
    }
}
