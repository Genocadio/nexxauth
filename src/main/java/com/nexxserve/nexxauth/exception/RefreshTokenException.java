package com.nexxserve.nexxauth.exception;

import org.springframework.http.HttpStatus;

public class RefreshTokenException extends ApiException {

    public RefreshTokenException(String message) {
        super(HttpStatus.UNAUTHORIZED, message);
    }
}
