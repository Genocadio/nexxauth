package com.nexxserve.nauth.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resolves the client IP for rate limiting and audit logging, honouring
 * {@code X-Forwarded-For} only when the deployment is behind a trusted proxy
 * (the {@code app.rate-limit.use-forwarded-for} flag). Shared so the rate
 * limiter and the audit log agree on who the client is.
 */
public final class ClientIps {

    private ClientIps() {
    }

    public static String resolve(HttpServletRequest request, boolean useForwardedFor) {
        if (useForwardedFor) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }
}
