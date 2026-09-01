package com.nexxserve.nexxauth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;

/**
 * Adds defensive security headers to every response. These are a defense-in-depth
 * layer — a reverse proxy (nginx, Cloudflare) may also set them, but the app
 * should not depend on the proxy being correctly configured.
 *
 * <ul>
 *   <li>{@code X-Content-Type-Options: nosniff} — prevents MIME-type sniffing</li>
 *   <li>{@code X-Frame-Options: DENY} — prevents clickjacking</li>
 *   <li>{@code Strict-Transport-Security} — forces HTTPS for 1 year (only on HTTPS)</li>
 *   <li>{@code Referrer-Policy: strict-origin-when-cross-origin} — limits referrer leakage</li>
 *   <li>{@code Permissions-Policy} — disables browser features the API doesn't need</li>
 *   <li>{@code Cache-Control: no-store} for auth endpoints — prevents credential caching</li>
 * </ul>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1) // after RequestIdFilter, before everything else
public class SecurityHeadersFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // --- always set ---
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        response.setHeader("Permissions-Policy",
                "accelerometer=(), camera=(), geolocation=(), gyroscope=(), " +
                "magnetometer=(), microphone=(), payment=(), usb=()");

        // --- HTTPS-only headers ---
        if (request.isSecure()) {
            response.setHeader("Strict-Transport-Security",
                    "max-age=31536000; includeSubDomains");
        }

        // --- cache control for sensitive endpoints ---
        String path = request.getRequestURI();
        if (isAuthPath(path) || isKeyPath(path)) {
            response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
            response.setHeader("Pragma", "no-cache");
        }

        filterChain.doFilter(request, response);
    }

    /** Auth endpoints that should never be cached by browsers or proxies. */
    private static boolean isAuthPath(String path) {
        return path.endsWith("/auth/login") || path.endsWith("/auth/register")
                || path.endsWith("/auth/refresh") || path.endsWith("/auth/logout")
                || path.equals("/auth/me") || path.equals("/auth/me/password");
    }

    /** Signing key endpoints — responses contain public keys, not secrets,
     *  but caching them could cause stale-key issues after rotation. */
    private static boolean isKeyPath(String path) {
        return path.endsWith("/keys") || path.endsWith("/keys/rotate");
    }
}
