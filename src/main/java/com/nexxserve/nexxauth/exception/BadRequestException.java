package com.nexxserve.nexxauth.exception;

import org.springframework.http.HttpStatus;

/**
 * The request is invalid in a way validation annotations cannot express
 * (e.g. a business rule depending on organisation settings).
 */
public class BadRequestException extends ApiException {

    public BadRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
