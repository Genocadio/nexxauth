package com.nexxserve.nexxauth.security;

import com.nexxserve.nexxauth.entity.LogLevel;
import com.nexxserve.nexxauth.exception.ErrorResponseWriter;
import com.nexxserve.nexxauth.service.AuthAuditService;
import com.nexxserve.nexxauth.util.ClientIps;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Applies the per-IP token bucket to the credential-based endpoints
 * (login/register/refresh, incl. org auth) and the public slug-suggestions
 * lookup before any auth work happens, so brute-force attempts are throttled
 * cheaply. Blocked requests get a 429 with a {@code Retry-After} header and
 * the unified error body.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String LOGIN_PATH = "/auth/login";
    private static final String REGISTER_PATH = "/auth/register";
    private static final String REFRESH_PATH = "/auth/refresh";
    private static final String SUGGESTIONS_PATH = "/slug-suggestions";
    // Platform auth is /auth/...; org auth is /{slug}/auth/... — one extra path
    // segment, which is how the two are told apart below.

    private final RateLimitService rateLimitService;
    private final RateLimitProperties properties;
    private final ErrorResponseWriter errorResponseWriter;
    private final AuthAuditService audit;

    public RateLimitFilter(RateLimitService rateLimitService, RateLimitProperties properties,
                           ErrorResponseWriter errorResponseWriter, AuthAuditService audit) {
        this.rateLimitService = rateLimitService;
        this.properties = properties;
        this.errorResponseWriter = errorResponseWriter;
        this.audit = audit;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        RateLimitProperties.Limit limit = limitFor(path);

        // Credential endpoints are POST-only; the public slug-suggestions
        // lookup is a GET that still needs the same per-IP protection.
        boolean rateLimited = limit != null
                && (HttpMethod.POST.matches(request.getMethod())
                || (HttpMethod.GET.matches(request.getMethod()) && SUGGESTIONS_PATH.equals(path)));

        if (rateLimited) {
            Optional<Long> retryAfter = rateLimitService.tryConsume(endpointFor(path) + ":"
                    + ClientIps.resolve(request, properties.isUseForwardedFor()), limit);
            if (retryAfter.isPresent()) {
                String ip = ClientIps.resolve(request, properties.isUseForwardedFor());
                String domain = extractDomain(request);
                String endpoint = endpointFor(path);
                audit.logRisk(LogLevel.WARN, AuthAuditService.RATE_LIMIT_EXCEEDED,
                        ip, domain,
                        "endpoint=" + endpoint + " retryAfter=" + retryAfter.get());
                response.setHeader(HttpHeaders.RETRY_AFTER, retryAfter.get().toString());
                errorResponseWriter.write(response, HttpStatus.TOO_MANY_REQUESTS,
                        "Too many requests, please try again later", path);
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private RateLimitProperties.Limit limitFor(String path) {
        if (LOGIN_PATH.equals(path) || isOrgAuth(path, "login")) {
            return properties.getLogin();
        }
        if (REGISTER_PATH.equals(path) || isOrgAuth(path, "register")) {
            return properties.getRegister();
        }
        if (REFRESH_PATH.equals(path) || isOrgAuth(path, "refresh")) {
            return properties.getRefresh();
        }
        if (SUGGESTIONS_PATH.equals(path)) {
            return properties.getSuggestions();
        }
        return null;
    }

    private String extractDomain(jakarta.servlet.http.HttpServletRequest request) {
        String host = request.getHeader("Host");
        if (host == null || host.isBlank()) return null;
        int colon = host.lastIndexOf(":");
        return colon > 0 ? host.substring(0, colon) : host;
    }

    private boolean isOrgAuth(String path, String endpoint) {
        // Org auth is /{slug}/auth/{endpoint} (a slug prefix); platform auth is
        // /auth/{endpoint}. The org form has exactly three path separators.
        return path.endsWith("/auth/" + endpoint)
                && path.chars().filter(c -> c == '/').count() == 3;
    }

    private String endpointFor(String path) {
        String endpoint = path.substring(path.lastIndexOf('/') + 1);
        // Org auth is a separate auth system: give it its own bucket so
        // brute-forcing org logins cannot exhaust the platform budget (and
        // vice versa).
        if (isOrgAuth(path, endpoint)) {
            return "org-" + endpoint;
        }
        return endpoint;
    }
}
