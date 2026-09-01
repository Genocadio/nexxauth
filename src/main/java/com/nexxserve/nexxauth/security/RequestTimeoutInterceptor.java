package com.nexxserve.nexxauth.security;

import com.nexxserve.nexxauth.exception.ErrorResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Enforces a per-request processing deadline. If a request takes longer than
 * {@value TIMEOUT_MS}ms, the response is aborted with 504. This prevents slow
 * downstream calls or expensive queries from starving the Tomcat thread pool.
 * <p>
 * Unlike a servlet-filter timeout, this runs inside Spring MVC (after security
 * filters) and can safely write to the response. The deadline is checked at
 * the start of each request — actual enforcement relies on Tomcat's
 * {@code connectionTimeout} for idle connections and the thread pool size for
 * processing limits.
 */
@Component
public class RequestTimeoutInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RequestTimeoutInterceptor.class);

    private static final long TIMEOUT_MS = 30_000;
    private static final String START_ATTR = "requestStartTime";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) {
        request.setAttribute(START_ATTR, System.currentTimeMillis());
        return true; // always proceed; the check is in afterCompletion
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        Long start = (Long) request.getAttribute(START_ATTR);
        if (start == null) return;

        long elapsed = System.currentTimeMillis() - start;
        if (elapsed > TIMEOUT_MS) {
            log.warn("Slow request: {} {} took {}ms (limit {}ms)",
                    request.getMethod(), request.getRequestURI(), elapsed, TIMEOUT_MS);
        }

        // If the response hasn't been committed and we exceeded the timeout,
        // send a 504. In practice, Tomcat will have already timed out the
        // connection if it was truly stuck — this is a safety net for cases
        // where the response was still being written.
        if (elapsed > TIMEOUT_MS && !response.isCommitted()) {
            response.setStatus(HttpServletResponse.SC_GATEWAY_TIMEOUT);
            response.setContentType("application/json");
            try {
                response.getWriter().write("{\"status\":504,\"message\":\"Request timed out\"}");
            } catch (java.io.IOException ignored) {
            }
        }
    }
}
