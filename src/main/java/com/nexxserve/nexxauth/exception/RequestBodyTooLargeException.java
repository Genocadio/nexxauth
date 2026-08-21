package com.nexxserve.nexxauth.exception;

/**
 * Thrown by the capped request stream in {@code PayloadSizeFilter} when a body
 * grows beyond {@code app.http.max-body-bytes} on a request that did not carry
 * a {@code Content-Length} (e.g. chunked transfer). The filter catches it and
 * answers 413 before the body can be buffered further.
 */
public class RequestBodyTooLargeException extends RuntimeException {

    public RequestBodyTooLargeException(long maxBytes) {
        super("Request body exceeds the maximum of " + maxBytes + " bytes");
    }
}
