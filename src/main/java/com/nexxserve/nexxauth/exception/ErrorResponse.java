package com.nexxserve.nexxauth.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;

/**
 * Single, consistent error body for the whole API. {@code requestId} lets
 * clients reference a specific request in the server logs.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        String requestId,
        List<FieldError> fieldErrors
) {

    public record FieldError(String field, String message) {
    }

    public static ErrorResponse of(HttpStatus status, String message, String path, String requestId) {
        return new ErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(), message, path, requestId, null);
    }

    public static ErrorResponse of(HttpStatus status, String message, String path, String requestId,
                                   List<FieldError> fieldErrors) {
        return new ErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(), message, path, requestId, fieldErrors);
    }
}
