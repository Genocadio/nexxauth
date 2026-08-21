package com.nexxserve.nauth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Generates (or accepts via {@code X-Request-Id}) a request id, echoes it on
 * the response and puts it in the MDC so every log line and error response can
 * be correlated. Runs before the security filter chain. Client-supplied ids
 * are sanitized (control characters stripped, length capped) so a malicious
 * value cannot inject into log lines.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    static final int MAX_LENGTH = 64;
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\p{Cntrl}]");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestId = sanitize(request.getHeader(HEADER));
        if (requestId == null) {
            requestId = UUID.randomUUID().toString();
        }
        response.setHeader(HEADER, requestId);
        MDC.put(MDC_KEY, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    /** Returns null when the header is absent/blank/whitespace; otherwise the
     * value with control characters removed, trimmed and capped at 64 chars. */
    static String sanitize(String raw) {
        if (raw == null) {
            return null;
        }
        String clean = CONTROL_CHARS.matcher(raw).replaceAll("").trim();
        if (clean.isEmpty()) {
            return null;
        }
        return clean.length() > MAX_LENGTH ? clean.substring(0, MAX_LENGTH) : clean;
    }
}
