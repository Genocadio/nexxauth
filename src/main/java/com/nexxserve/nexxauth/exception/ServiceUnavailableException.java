package com.nexxserve.nexxauth.exception;

import org.springframework.http.HttpStatus;

/**
 * An upstream dependency (currently the nexxbotify notification service) is
 * temporarily unavailable and the request cannot be fulfilled.
 */
public class ServiceUnavailableException extends ApiException {

    public ServiceUnavailableException(String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, message);
    }
}