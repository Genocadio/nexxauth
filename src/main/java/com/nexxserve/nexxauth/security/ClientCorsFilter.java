package com.nexxserve.nexxauth.security;

import com.nexxserve.nexxauth.entity.LogLevel;
import com.nexxserve.nexxauth.entity.OrganisationClient;
import com.nexxserve.nexxauth.repository.OrganisationClientRepository;
import com.nexxserve.nexxauth.service.AuthAuditService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Applies the CORS headers a client is entitled to. When a request from a
 * client (identified via {@code X-Client-Id}) carries an {@code Origin} that is
 * in the client's configured allowed origins, the origin is echoed on the
 * response. Works for every client type that has trusted origins configured
 * (web clients are the typical case); requests from origins that are not
 * trusted get no CORS headers and are blocked by the browser.
 * <p>
 * Preflight {@code OPTIONS} requests never carry custom headers — browsers
 * strip them, so {@code X-Client-Id} cannot identify the client yet. A
 * preflight is answered whenever <em>any</em> enabled client trusts the
 * {@code Origin}; the real request that follows still needs its own matching
 * {@code X-Client-Id}, so this widens nothing beyond configured origins.
 * <p>
 * Runs before Spring Security (and the rate-limit filter) so preflight
 * {@code OPTIONS} are answered directly without hitting auth.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class ClientCorsFilter extends OncePerRequestFilter {

    private static final String CLIENT_ID_HEADER = "X-Client-Id";
    private static final String ORIGIN = "Origin";
    private static final String ALLOWED_METHODS = "GET, POST, PATCH, DELETE, OPTIONS";
    private static final String ALLOWED_HEADERS = "Content-Type, Authorization, X-Client-Id, X-Request-Id";
    private static final String EXPOSED_HEADERS = "X-Request-Id";

    private final OrganisationClientRepository clientRepository;
    private final AuthAuditService audit;

    public ClientCorsFilter(OrganisationClientRepository clientRepository, AuthAuditService audit) {
        this.clientRepository = clientRepository;
        this.audit = audit;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String origin = request.getHeader(ORIGIN);
        boolean preflight = isPreflight(request);
        if (origin == null || origin.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIdHeader = request.getHeader(CLIENT_ID_HEADER);
        if (clientIdHeader != null) {
            OrganisationClient client = findEnabledClient(clientIdHeader);
            if (client == null || !allowedOrigins(client).contains(origin)) {
                // Unknown/disabled client or untrusted origin: log the rejection
                String ip = com.nexxserve.nexxauth.util.ClientIps.resolve(request, false);
                String domain = extractDomain(request);
                audit.logRisk(LogLevel.WARN, AuthAuditService.CORS_ORIGIN_REJECTED,
                        ip, domain,
                        "origin=" + origin + " clientId=" + clientIdHeader);
                filterChain.doFilter(request, response);
                return;
            }
            applyCorsHeaders(response, origin);
            if (preflight) {
                applyPreflightHeaders(response);
                response.setStatus(HttpServletResponse.SC_OK);
                return;
            }
            filterChain.doFilter(request, response);
            return;
        }

        // No X-Client-Id: browsers strip custom headers from preflights, so the
        // client cannot be identified yet. Answer the preflight when any enabled
        // client trusts this origin — the real request still carries its own
        // X-Client-Id and must match that client's origins to be served.
        if (preflight && anyEnabledClientTrusts(origin)) {
            applyCorsHeaders(response, origin);
            applyPreflightHeaders(response);
            response.setStatus(HttpServletResponse.SC_OK);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private OrganisationClient findEnabledClient(String clientIdHeader) {
        String key = clientIdHeader.trim();
        if (key.isEmpty()) {
            return null;
        }
        OrganisationClient client = clientRepository.findByClientKey(key).orElse(null);
        return client != null && client.isEnabled() ? client : null;
    }

    private boolean anyEnabledClientTrusts(String origin) {
        for (OrganisationClient client : clientRepository.findByEnabledTrue()) {
            if (allowedOrigins(client).contains(origin)) {
                return true;
            }
        }
        return false;
    }

    private static void applyCorsHeaders(HttpServletResponse response, String origin) {
        response.setHeader("Access-Control-Allow-Origin", origin);
        response.addHeader("Vary", "Origin");
        response.setHeader("Access-Control-Expose-Headers", EXPOSED_HEADERS);
    }

    private static void applyPreflightHeaders(HttpServletResponse response) {
        response.setHeader("Access-Control-Allow-Methods", ALLOWED_METHODS);
        response.setHeader("Access-Control-Allow-Headers", ALLOWED_HEADERS);
        response.setHeader("Access-Control-Max-Age", "3600");
    }

    private String extractDomain(HttpServletRequest request) {
        String host = request.getHeader("Host");
        if (host == null || host.isBlank()) return null;
        int colon = host.lastIndexOf(":");
        return colon > 0 ? host.substring(0, colon) : host;
    }

    private static boolean isPreflight(HttpServletRequest request) {
        return "OPTIONS".equalsIgnoreCase(request.getMethod())
                && request.getHeader("Access-Control-Request-Method") != null;
    }

    private static Set<String> allowedOrigins(OrganisationClient client) {
        String raw = client.getAllowedOrigins();
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }
}
