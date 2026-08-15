package com.nexxserve.nexxauth.security;

import com.nexxserve.nexxauth.entity.OrganisationClient;
import com.nexxserve.nexxauth.entity.Permission;
import com.nexxserve.nexxauth.exception.ErrorResponseWriter;
import com.nexxserve.nexxauth.repository.OrganisationClientRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Enforces the client access rules for requests carrying {@code X-Client-Id}:
 * <ul>
 *   <li>unknown or disabled clients are rejected (401/403);</li>
 *   <li>clients that require authentication must present their static token as
 *       {@code Authorization: Bearer <token>} — the valid token authenticates
 *       the request (scoped to the client's organisation) for the full org
 *       API;</li>
 *   <li>clients that do not require authentication (web, and apps without
 *       auth) may reach the organisation login/register endpoints anonymously,
 *       and — when the request also carries a valid org-user JWT (already set
 *       by the {@link OrgJwtAuthenticationFilter}) — the org user proceeds to
 *       the org API under their own roles/permissions instead of being blocked.</li>
 * </ul>
 * Runs after the JWT filters, so a present {@code X-Client-Id} always wins
 * over a bearer JWT for auth-required clients; for no-auth clients a valid
 * org-user JWT takes precedence (the client identity is not used for access).
 */
@Component
public class ClientTokenFilter extends OncePerRequestFilter {

    static final String CLIENT_ID_HEADER = "X-Client-Id";

    private static final String BEARER_PREFIX = "Bearer ";
    private static final Pattern ORG_AUTH_PATH =
            Pattern.compile("^/api/v1/platforms/[^/]+/auth/(login|register)$");
    private static final List<SimpleGrantedAuthority> CLIENT_AUTHORITIES = clientAuthorities();

    private final OrganisationClientRepository clientRepository;
    private final ErrorResponseWriter errorResponseWriter;

    public ClientTokenFilter(OrganisationClientRepository clientRepository, ErrorResponseWriter errorResponseWriter) {
        this.clientRepository = clientRepository;
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String clientIdHeader = request.getHeader(CLIENT_ID_HEADER);
        if (clientIdHeader == null) {
            filterChain.doFilter(request, response);
            return;
        }

        Long clientId = parseId(clientIdHeader);
        OrganisationClient client = clientId == null ? null
                : clientRepository.findById(clientId).orElse(null);
        if (client == null) {
            write(response, HttpStatus.UNAUTHORIZED, "Unknown client", request.getRequestURI());
            return;
        }
        if (!client.isEnabled()) {
            write(response, HttpStatus.FORBIDDEN, "Client is disabled", request.getRequestURI());
            return;
        }

        if (client.isRequireAuthentication()) {
            String header = request.getHeader("Authorization");
            String token = header != null && header.startsWith(BEARER_PREFIX)
                    ? header.substring(BEARER_PREFIX.length())
                    : null;
            if (token == null || !ClientTokens.matches(token, client.getTokenHash())) {
                write(response, HttpStatus.UNAUTHORIZED, "Invalid client token", request.getRequestURI());
                return;
            }
            ClientPrincipal principal = new ClientPrincipal(
                    client.getId(), client.getName(), client.getType(), client.getOrganisation().getId());
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(principal, null, CLIENT_AUTHORITIES));
            filterChain.doFilter(request, response);
            return;
        }

        // No-auth client (web, or apps without auth): the client itself cannot
        // reach the org API beyond login/register, but a valid org-user JWT
        // (authenticated earlier by OrgJwtAuthenticationFilter) proceeds under
        // that user's own roles/permissions.
        if (isOrgUserAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!ORG_AUTH_PATH.matcher(request.getRequestURI()).matches()) {
            write(response, HttpStatus.FORBIDDEN,
                    "This client type can only access the organisation login and register endpoints",
                    request.getRequestURI());
            return;
        }
        filterChain.doFilter(request, response);
    }

    /** True when the request already carries a valid org-user JWT. */
    private boolean isOrgUserAuthenticated() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof OrgUserPrincipal;
    }

    private Long parseId(String raw) {
        try {
            long id = Long.parseLong(raw.trim());
            return id > 0 ? id : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void write(HttpServletResponse response, HttpStatus status, String message, String path)
            throws IOException {
        errorResponseWriter.write(response, status, message, path);
    }

    private static List<SimpleGrantedAuthority> clientAuthorities() {
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ORG_USER"));
        for (Permission permission : Permission.values()) {
            authorities.add(new SimpleGrantedAuthority("PERM_" + permission.name()));
        }
        return List.copyOf(authorities);
    }
}
