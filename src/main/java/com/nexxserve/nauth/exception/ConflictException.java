package com.nexxserve.nauth.exception;

import org.springframework.http.HttpStatus;

/**
 * A resource already exists (e.g. duplicate email or slug).
 */
public class ConflictException extends ApiException {

    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
