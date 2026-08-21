package com.nexxserve.nexxauth.exception;

import com.nexxserve.nexxauth.security.RequestIdFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * Writes the unified {@link ErrorResponse} JSON from code that runs outside the
 * DispatcherServlet (security chain, rate-limit filter), where
 * {@code @RestControllerAdvice} cannot intercept. Always includes the current
 * request id from the MDC.
 */
@Component
public class ErrorResponseWriter {

    private final ObjectMapper objectMapper;

    public ErrorResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletResponse response, HttpStatus status, String message, String path)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(),
                ErrorResponse.of(status, message, path, MDC.get(RequestIdFilter.MDC_KEY)));
    }
}
