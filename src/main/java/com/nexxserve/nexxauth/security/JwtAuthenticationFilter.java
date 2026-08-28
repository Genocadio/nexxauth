package com.nexxserve.nexxauth.security;

import com.nexxserve.nexxauth.entity.LogCategory;
import com.nexxserve.nexxauth.entity.LogLevel;
import com.nexxserve.nexxauth.entity.PlatformUser;
import com.nexxserve.nexxauth.repository.PlatformUserRepository;
import com.nexxserve.nexxauth.service.AuthAuditService;
import com.nexxserve.nexxauth.service.LogService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Reads {@code Authorization: Bearer <access token>} and, when the signature is
 * valid, re-validates the user against the database before authenticating. This
 * makes account disabling and role changes take effect immediately instead of
 * trusting potentially stale token claims for the whole token lifetime. Invalid
 * or disabled users are simply left unauthenticated so the entry point answers
 * 401.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final PlatformUserRepository platformUserRepository;
    private final LogService logService;

    public JwtAuthenticationFilter(JwtService jwtService, PlatformUserRepository platformUserRepository, LogService logService) {
        this.jwtService = jwtService;
        this.platformUserRepository = platformUserRepository;
        this.logService = logService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                Claims claims = jwtService.parseAccessToken(header.substring(BEARER_PREFIX.length()));
                Long userId = Long.valueOf(claims.getSubject());
                platformUserRepository.findWithPlatformById(userId)
                        .filter(PlatformUser::isEnabled)
                        .ifPresent(user -> {
                            AuthenticatedUser principal = new AuthenticatedUser(
                                    user.getId(), user.getEmail(), user.getRole(), user.getPlatform().getId());
                            UsernamePasswordAuthenticationToken authentication =
                                    new UsernamePasswordAuthenticationToken(principal, null,
                                            List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
                            SecurityContextHolder.getContext().setAuthentication(authentication);
                        });
            } catch (JwtException | IllegalArgumentException e) {
                // Log invalid/expired JWT token — leave unauthenticated -> entry point answers 401
                try {
                    logService.logEvent(LogLevel.WARN, LogCategory.SECURITY,
                            AuthAuditService.PLATFORM_LOGIN_FAILURE, "Invalid or expired JWT token",
                            null, null, null, e.getMessage(), null, null);
                } catch (Exception ignored) {
                    // Never let logging break the filter
                }
            }
        }
        filterChain.doFilter(request, response);
    }
}
