package com.nexxserve.nexxauth.service;

import com.nexxserve.nexxauth.entity.LogCategory;
import com.nexxserve.nexxauth.entity.LogLevel;
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
    private final LogService logService;

    public AuthAuditService(RateLimitProperties rateLimitProperties, LogService logService) {
        this.rateLimitProperties = rateLimitProperties;
        this.logService = logService;
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

    /** User management events (platform + org). */
    public static final String PLATFORM_USER_ADDED = "PLATFORM_USER_ADDED";
    public static final String ORG_USER_CREATED = "ORG_USER_CREATED";
    public static final String ORG_USER_UPDATED = "ORG_USER_UPDATED";
    public static final String ORG_USER_DELETED = "ORG_USER_DELETED";

    /** Organisation management events. */
    public static final String ORG_CREATED = "ORG_CREATED";
    public static final String ORG_UPDATED = "ORG_UPDATED";
    public static final String ORG_DELETED = "ORG_DELETED";

    /** Organisation role events. */
    public static final String ORG_ROLE_CREATED = "ORG_ROLE_CREATED";
    public static final String ORG_ROLE_UPDATED = "ORG_ROLE_UPDATED";
    public static final String ORG_ROLE_DELETED = "ORG_ROLE_DELETED";

    /** Organisation client events. */
    public static final String ORG_CLIENT_CREATED = "ORG_CLIENT_CREATED";
    public static final String ORG_CLIENT_UPDATED = "ORG_CLIENT_UPDATED";
    public static final String ORG_CLIENT_DELETED = "ORG_CLIENT_DELETED";
    public static final String ORG_CLIENT_TOKEN_ROTATED = "ORG_CLIENT_TOKEN_ROTATED";

    /** Organisation session events. */
    public static final String ORG_SESSION_REVOKED = "ORG_SESSION_REVOKED";
    public static final String ORG_SESSIONS_REVOKED_ALL = "ORG_SESSIONS_REVOKED_ALL";

    /** Risk / security events. */
    public static final String RATE_LIMIT_EXCEEDED = "RATE_LIMIT_EXCEEDED";
    public static final String CORS_ORIGIN_REJECTED = "CORS_ORIGIN_REJECTED";

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

    /**
     * Persist and broadcast a log entry to SSE clients, in addition to the
     * console log line above. Organisation-scoped events pass the org id;
     * platform-level events pass {@code null}.
     */
    public void logPersisted(LogLevel level, LogCategory category, String event, String actor,
                             String organisationSlug, Long organisationId, String detail) {
        // Still emit the console line with the slug (tests assert on this format)
        HttpServletRequest request = currentRequest();
        String ip = request != null
                ? ClientIps.resolve(request, useForwardedFor(request))
                : "-";
        AUDIT.info("AUDIT event={} actor={} organisation={} detail={} ip={}",
                event, sanitize(actor), organisationSlug == null ? "-" : organisationSlug,
                sanitize(detail) == null ? "-" : sanitize(detail), ip);

        // Persist to the log_entries table and broadcast via SSE
        try {
            logService.logEvent(level, category, event, sanitize(event),
                    null, organisationId, sanitize(actor), sanitize(detail), null, null);
        } catch (Exception e) {
            // Never let logging break the caller
            AUDIT.warn("Failed to persist log entry: {}", e.getMessage());
        }
    }

    /**
     * Log a risk event from a servlet filter where the request context may
     * not be fully available. Accepts IP and domain explicitly.
     */
    public void logRisk(LogLevel level, String event, String ip, String domain, String detail) {
        AUDIT.warn("AUDIT event={} ip={} domain={} detail={}",
                event, ip == null ? "-" : ip,
                domain == null ? "-" : domain,
                sanitize(detail) == null ? "-" : sanitize(detail));
        try {
            logService.logEvent(level, LogCategory.SECURITY, event, sanitize(event),
                    null, null, "system", sanitize(detail), null, domain);
        } catch (Exception e) {
            AUDIT.warn("Failed to persist risk log entry: {}", e.getMessage());
        }
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
