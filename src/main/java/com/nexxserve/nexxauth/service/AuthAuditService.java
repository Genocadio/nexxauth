package com.nexxserve.nexxauth.service;

import com.nexxserve.nexxauth.security.RateLimitProperties;
import com.nexxserve.nexxauth.util.ClientIps;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Security-relevant audit events (login success/failure, registrations, token
 * rotation, key rotation, session revocations). Written to the dedicated
 * {@code AUDIT} logger at INFO: in production the ECS structured logging turns
 * every line into one JSON document, and the {@code requestId} (from
 * {@code RequestIdFilter}'s MDC) correlates the event with the full request
 * trail. Events are also designed to be greppable in dev:
 *
 * <pre>
 *   AUDIT event=ORG_LOGIN_SUCCESS actor=jane org=oa-org ip=127.0.0.1
 * </pre>
 *
 * This is a log-based trail on purpose: it never touches the request/response
 * path, can never fail the caller, and the format can be shipped to any log
 * aggregator. For a queryable audit store, persist these events to a table
 * instead of / in addition to logging.
 */
@Component
public class AuthAuditService {

    public static final String LOGGER_NAME = "AUDIT";

    private static final Logger AUDIT = LoggerFactory.getLogger(LOGGER_NAME);

    private final RateLimitProperties rateLimitProperties;

    public AuthAuditService(RateLimitProperties rateLimitProperties) {
        this.rateLimitProperties = rateLimitProperties;
    }

    /** Platform auth events. */
    public static final String PLATFORM_REGISTER = "PLATFORM_REGISTER";
    public static final String PLATFORM_LOGIN_SUCCESS = "PLATFORM_LOGIN_SUCCESS";
    public static final String PLATFORM_LOGIN_FAILURE = "PLATFORM_LOGIN_FAILURE";
    public static final String PLATFORM_REFRESH = "PLATFORM_REFRESH";
    public static final String PLATFORM_LOGOUT = "PLATFORM_LOGOUT";
    public static final String PLATFORM_PASSWORD_CHANGED = "PLATFORM_PASSWORD_CHANGED";
    public static final String PLATFORM_TOKEN_REUSE = "PLATFORM_TOKEN_REUSE";
    public static final String PLATFORM_DISABLED = "PLATFORM_DISABLED";

    /** Organisation auth events. */
    public static final String ORG_REGISTER = "ORG_REGISTER";
    public static final String ORG_LOGIN_SUCCESS = "ORG_LOGIN_SUCCESS";
    public static final String ORG_LOGIN_FAILURE = "ORG_LOGIN_FAILURE";
    public static final String ORG_REFRESH = "ORG_REFRESH";
    public static final String ORG_LOGOUT = "ORG_LOGOUT";
    public static final String ORG_PASSWORD_CHANGED = "ORG_PASSWORD_CHANGED";
    public static final String ORG_KEY_ROTATED = "ORG_KEY_ROTATED";
    public static final String ORG_TOKEN_REUSE = "ORG_TOKEN_REUSE";

    /** Organisation user-field configuration events. */
    public static final String ORG_USER_FIELD_CREATED = "ORG_USER_FIELD_CREATED";
    public static final String ORG_USER_FIELD_UPDATED = "ORG_USER_FIELD_UPDATED";
    public static final String ORG_USER_FIELD_DELETED = "ORG_USER_FIELD_DELETED";

    public void log(String event, String actor, String organisation) {
        log(event, actor, organisation, null);
    }

    public void log(String event, String actor, String organisation, String detail) {
        HttpServletRequest request = currentRequest();
        String ip = request != null
                ? ClientIps.resolve(request, useForwardedFor(request))
                : "-";
        AUDIT.info("AUDIT event={} actor={} organisation={} detail={} ip={}",
                event, sanitize(actor), organisation == null ? "-" : organisation,
                sanitize(detail) == null ? "-" : sanitize(detail), ip);
    }

    /** Strips control characters from attacker-controllable values (login
     * identifiers echoed into audit lines), so no input can forge or break a
     * log line even in plain-console dev output. */
    private String sanitize(String value) {
        if (value == null) {
            return null;
        }
        StringBuilder cleaned = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < 0x20 || (c >= 0x7f && c < 0xa0)) {
                continue;
            }
            cleaned.append(c);
        }
        return cleaned.toString();
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    /** Same trusted-proxy setting the rate limiter uses, so audit and limiter
     * agree on the client IP. Without a request context (scheduled jobs) there
     * is no client, so plain remote. */
    private boolean useForwardedFor(HttpServletRequest request) {
        return rateLimitProperties.isUseForwardedFor();
    }
}
