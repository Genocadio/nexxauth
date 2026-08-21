package com.nexxserve.nexxauth.exception;

import com.nexxserve.nexxauth.security.RequestIdFilter;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Translates every exception the framework can throw into a single, stable
 * {@link ErrorResponse} shape with the correct HTTP status. Unexpected errors
 * are logged with the request id and never leak internal details.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // --- application exceptions (intentional, carry their own status) ---

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException ex, HttpServletRequest request) {
        return build(ex.getStatus(), ex.getMessage(), request, null);
    }

    // --- client errors: 400 ---

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleBodyValidation(MethodArgumentNotValidException ex,
                                                              HttpServletRequest request) {
        List<ErrorResponse.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ErrorResponse.FieldError(fe.getField(), fe.getDefaultMessage()))
                .collect(Collectors.toList());
        return build(HttpStatus.BAD_REQUEST, "Validation failed", request, fieldErrors);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleMethodValidation(HandlerMethodValidationException ex,
                                                                HttpServletRequest request) {
        List<ErrorResponse.FieldError> fieldErrors = ex.getParameterValidationResults().stream()
                .map(result -> new ErrorResponse.FieldError(
                        result.getMethodParameter().getParameterName(),
                        result.getResolvableErrors().stream()
                                .map(resolvable -> resolvable.getDefaultMessage())
                                .collect(Collectors.joining(", "))))
                .collect(Collectors.toList());
        return build(HttpStatus.BAD_REQUEST, "Validation failed", request, fieldErrors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex,
                                                                   HttpServletRequest request) {
        List<ErrorResponse.FieldError> fieldErrors = ex.getConstraintViolations().stream()
                .map(violation -> new ErrorResponse.FieldError(
                        violation.getPropertyPath().toString(), violation.getMessage()))
                .collect(Collectors.toList());
        return build(HttpStatus.BAD_REQUEST, "Validation failed", request, fieldErrors);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                            HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST,
                "Invalid value for parameter '" + ex.getName() + "'", request, null);
    }

    @ExceptionHandler(ServletRequestBindingException.class)
    public ResponseEntity<ErrorResponse> handleBinding(ServletRequestBindingException ex,
                                                       HttpServletRequest request) {
        // Never echo the framework's message (it leaks method signatures).
        return build(HttpStatus.BAD_REQUEST, "Missing or malformed request parameter or header", request, null);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex,
                                                          HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Malformed or missing request body", request, null);
    }

    // --- client errors: 404 / 405 / 406 / 415 ---

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ErrorResponse> handleNotFound(Exception ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "No resource found at " + request.getRequestURI(), request, null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                                                  HttpServletRequest request) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, ex.getMessage(), request, null);
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ErrorResponse> handleNotAcceptable(HttpMediaTypeNotAcceptableException ex,
                                                             HttpServletRequest request) {
        return build(HttpStatus.NOT_ACCEPTABLE, "Requested media type is not acceptable", request, null);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex,
                                                                     HttpServletRequest request) {
        return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ex.getMessage(), request, null);
    }

    // --- conflict: 409 ---

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex,
                                                             HttpServletRequest request) {
        // e.g. unique-constraint race between check and insert; do not leak constraint details
        log.warn("Data integrity violation on {} (requestId={})", request.getRequestURI(), requestId(), ex);
        return build(HttpStatus.CONFLICT, "The request conflicts with existing data", request, null);
    }

    // --- security: 401 / 403 (safety nets; the security chain normally handles these first) ---

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex,
                                                            HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, "Insufficient permissions", request, null);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException ex,
                                                              HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, "Authentication required", request, null);
    }

    // --- persistence ---

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleJpaEntityNotFound(EntityNotFoundException ex,
                                                                 HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "Requested resource was not found", request, null);
    }

    // --- anything unexpected: 500 (never leak internals) ---

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {} (requestId={})", request.getRequestURI(), requestId(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", request, null);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message, HttpServletRequest request,
                                                List<ErrorResponse.FieldError> fieldErrors) {
        ErrorResponse body = fieldErrors == null
                ? ErrorResponse.of(status, message, request.getRequestURI(), requestId())
                : ErrorResponse.of(status, message, request.getRequestURI(), requestId(), fieldErrors);
        return ResponseEntity.status(status).body(body);
    }

    private String requestId() {
        return MDC.get(RequestIdFilter.MDC_KEY);
    }
}
