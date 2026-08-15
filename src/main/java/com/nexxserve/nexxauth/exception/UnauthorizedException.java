package com.nexxserve.nexxauth.exception;

import org.springframework.http.HttpStatus;

/**
 * The request requires authentication (e.g. an organisation slug lookup
 * without a platform token). Maps to 401 with the unified error body.
 */
public class UnauthorizedException extends ApiException {

    public UnauthorizedException(String message) {
        super(HttpStatus.UNAUTHORIZED, message);
    }
}
