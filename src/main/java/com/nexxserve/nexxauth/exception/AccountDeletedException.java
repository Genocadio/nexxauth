package com.nexxserve.nexxauth.exception;

import org.springframework.http.HttpStatus;

public class AccountDeletedException extends ApiException {

    public AccountDeletedException() {
        super(HttpStatus.FORBIDDEN, "This account has been deleted");
    }

    public AccountDeletedException(String message) {
        super(HttpStatus.FORBIDDEN, message);
    }
}
