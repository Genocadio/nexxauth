package com.nexxserve.nexxauth.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base class for all expected application errors. Each subclass declares the
 * HTTP status it maps to via the global exception handler.
 */
@Getter
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;

    protected ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }
}
