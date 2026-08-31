package com.nexxserve.nexxauth.security;

import com.nexxserve.nexxauth.entity.LogLevel;
import com.nexxserve.nexxauth.entity.OrganisationClient;
import com.nexxserve.nexxauth.entity.OrganisationClientLink;
import com.nexxserve.nexxauth.repository.OrganisationClientLinkRepository;
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
import java.util.List;

/**
 * Applies the CORS headers a client is entitled to. When a request from a
 * client (identified via {@code X-Client-Id}) carries an {@code Origin} that
 * matches a link with {@code allowCors = true}, the origin is echoed on the
 * response. Requests from origins that are not trusted get no CORS headers
 * and are blocked by the browser.
 * <p>
 * When any link has {@code limitSource = true}, only requests whose
 * {@code Origin} matches one of those links are allowed through — all other
 * origins are rejected (403). This enforces per-origin source restrictions.
 * <p>
 * Preflight {@code OPTIONS} requests never carry custom headers — browsers
 * strip them, so {@code X-Client-Id} cannot identify the client yet. A
 * preflight is answered whenever <em>any</em> enabled client link trusts the
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
    private final OrganisationClientLinkRepository linkRepository;
    private final AuthAuditService audit;

    public ClientCorsFilter(OrganisationClientRepository clientRepository,
                             OrganisationClientLinkRepository linkRepository,
                             AuthAuditService audit) {
        this.clientRepository = clientRepository;
        this.linkRepository = linkRepository;
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
            if (client == null) {
                String ip = com.nexxserve.nexxauth.util.ClientIps.resolve(request, false);
                String domain = extractDomain(request);
                audit.logRisk(LogLevel.WARN, AuthAuditService.CORS_ORIGIN_REJECTED,
                        ip, domain,
                        "origin=" + origin + " clientId=" + clientIdHeader + " reason=unknown_client");
                filterChain.doFilter(request, response);
                return;
            }

            List<OrganisationClientLink> links = linkRepository.findByClientIdOrderByIdAsc(client.getId());

            // Limit source check: if any link has limitSource = true, the request
            // must come from one of those origins.
            if (hasLimitSource(links) && !matchesLimitSource(links, origin)) {
                String ip = com.nexxserve.nexxauth.util.ClientIps.resolve(request, false);
                String domain = extractDomain(request);
                audit.logRisk(LogLevel.WARN, AuthAuditService.CORS_ORIGIN_REJECTED,
                        ip, domain,
                        "origin=" + origin + " clientId=" + clientIdHeader + " reason=limit_source");
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json");
                response.getWriter().write("{\"message\":\"Origin not allowed by source restriction\"}");
                return;
            }

            // CORS check: find a matching link with allowCors = true.
            OrganisationClientLink matchingLink = findCorsLink(links, origin);
            if (matchingLink == null) {
                // No CORS link matches — log and pass through (browser blocks)
                String ip = com.nexxserve.nexxauth.util.ClientIps.resolve(request, false);
                String domain = extractDomain(request);
                audit.logRisk(LogLevel.WARN, AuthAuditService.CORS_ORIGIN_REJECTED,
                        ip, domain,
                        "origin=" + origin + " clientId=" + clientIdHeader + " reason=no_cors_link");
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
        // client link trusts this origin — the real request still carries its own
        // X-Client-Id and must match that client's origins to be served.
        if (preflight && anyEnabledClientLinkTrusts(origin)) {
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

    /** True when any link for this client has limitSource = true. */
    private static boolean hasLimitSource(List<OrganisationClientLink> links) {
        return links.stream().anyMatch(OrganisationClientLink::isLimitSource);
    }

    /** True when the origin matches a link with limitSource = true. */
    private static boolean matchesLimitSource(List<OrganisationClientLink> links, String origin) {
        return links.stream()
                .filter(OrganisationClientLink::isLimitSource)
                .anyMatch(link -> link.getOrigin().equalsIgnoreCase(origin));
    }

    /** Find the first link whose origin matches and has allowCors = true. */
    private static OrganisationClientLink findCorsLink(List<OrganisationClientLink> links, String origin) {
        return links.stream()
                .filter(OrganisationClientLink::isAllowCors)
                .filter(link -> link.getOrigin().equalsIgnoreCase(origin))
                .findFirst()
                .orElse(null);
    }

    private boolean anyEnabledClientLinkTrusts(String origin) {
        for (OrganisationClient client : clientRepository.findByEnabledTrue()) {
            List<OrganisationClientLink> links = linkRepository.findByClientIdOrderByIdAsc(client.getId());
            if (findCorsLink(links, origin) != null) {
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
}
