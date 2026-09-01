package com.nexxserve.nexxauth.security;

import com.nexxserve.nexxauth.entity.Organisation;
import com.nexxserve.nexxauth.entity.OrganisationClient;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Allows external clients to call organisation endpoints without the
 * {@code organisationId} in the URL path. When the request carries an
 * {@code X-Client-Id} header and the path is {@code /{slug}/organisations/**}
 * (no numeric segment after {@code organisations/}), this filter resolves the
 * organisation from the client and rewrites the URL to include the id.
 * <p>
 * Examples:
 * <ul>
 *   <li>{@code GET /acme/organisations/users} + {@code X-Client-Id: cli_...}
 *       → rewrites to {@code GET /acme/organisations/7/users} (if client's org id is 7)</li>
 *   <li>{@code GET /acme/organisations/7/users} → passes through unchanged</li>
 *   <li>{@code GET /acme/organisations/health} → passes through (special path)</li>
 * </ul>
 * Platform users (console) always include the organisationId in the path and
 * are unaffected.
 */
@Component
public class OrganisationIdFilter extends OncePerRequestFilter {

    static final String CLIENT_ID_HEADER = "X-Client-Id";

    /**
     * Matches: /{slug}/organisations/{anything}
     * Group 1: slug
     * Group 2: what comes after organisations/
     */
    private static final Pattern ORG_PATH =
            Pattern.compile("^/([^/]+)/organisations/(.+)$");

    /** Matches a numeric id segment (e.g. "7" or "7/users"). */
    private static final Pattern NUMERIC_START =
            Pattern.compile("^\\d+(/.*)?$");

    /** Paths that should never be rewritten. */
    private static final Pattern SKIP_PATHS =
            Pattern.compile("^/(health|docs).*");

    private final ClientCache clientCache;

    public OrganisationIdFilter(ClientCache clientCache) {
        this.clientCache = clientCache;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String clientId = request.getHeader(CLIENT_ID_HEADER);

        // Only rewrite when X-Client-Id is present and path matches the pattern
        if (clientId != null && !clientId.isBlank()) {
            String uri = request.getRequestURI();
            Matcher m = ORG_PATH.matcher(uri);
            if (m.matches()) {
                String afterOrg = m.group(2);

                // Skip if path already has a numeric org id (e.g. /organisations/7/users)
                if (NUMERIC_START.matcher(afterOrg).matches()) {
                    filterChain.doFilter(request, response);
                    return;
                }

                // Skip special paths like /organisations/health, /organisations/docs
                if (SKIP_PATHS.matcher(afterOrg).matches()) {
                    filterChain.doFilter(request, response);
                    return;
                }

                // Resolve org from client (cached — OrganisationIdFilter and
                // ClientTokenFilter share this cache to avoid a redundant DB query)
                OrganisationClient client = clientCache.findByClientKey(clientId.trim()).orElse(null);
                if (client != null) {
                    Organisation org = client.getOrganisation();
                    String slug = m.group(1);
                    String rewritten = "/" + slug + "/organisations/" + org.getId() + "/" + afterOrg;
                    // Remove trailing slash if present
                    if (rewritten.endsWith("/")) {
                        rewritten = rewritten.substring(0, rewritten.length() - 1);
                    }
                    filterChain.doFilter(new RewrittenRequest(request, rewritten), response);
                    return;
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    /** HttpServletRequestWrapper that overrides URI and servlet path. */
    private static class RewrittenRequest extends HttpServletRequestWrapper {
        private final String newUri;

        RewrittenRequest(HttpServletRequest request, String newUri) {
            super(request);
            this.newUri = newUri;
        }

        @Override
        public String getRequestURI() {
            return newUri;
        }

        @Override
        public String getServletPath() {
            return newUri;
        }

        @Override
        public StringBuffer getRequestURL() {
            StringBuffer url = new StringBuffer();
            String scheme = getScheme();
            url.append(scheme).append("://").append(getServerName());
            int port = getServerPort();
            if (("https".equals(scheme) && port != 443) || ("http".equals(scheme) && port != 80)) {
                url.append(':').append(port);
            }
            url.append(newUri);
            return url;
        }
    }
}
