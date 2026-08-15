package com.nexxserve.nexxauth.security;

import com.nexxserve.nexxauth.entity.OrganisationUser;
import com.nexxserve.nexxauth.entity.Permission;
import com.nexxserve.nexxauth.repository.OrganisationUserRepository;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reads {@code Authorization: Bearer <org access token>} (signed by the
 * organisation's own RSA key) and, when the signature is valid, re-validates
 * the org user against the database before authenticating - so disabled users
 * and role/permission changes take effect immediately. Runs after the platform
 * filter; org tokens are simply ignored by the platform filter (wrong key).
 */
@Component
public class OrgJwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final OrgJwtService orgJwtService;
    private final OrganisationUserRepository organisationUserRepository;

    public OrgJwtAuthenticationFilter(OrgJwtService orgJwtService,
                                      OrganisationUserRepository organisationUserRepository) {
        this.orgJwtService = orgJwtService;
        this.organisationUserRepository = organisationUserRepository;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Org tokens only ever apply to organisation-scoped endpoints
        // (including the bare list path .../organisations). On any other path
        // (platform auth, platform users, ...) an org token must stay
        // unauthenticated so the platform entry point answers 401.
        return !request.getRequestURI().matches("^/api/v1/platforms/[^/]+/organisations(/.*)?$");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                Claims claims = orgJwtService.parseAccessToken(header.substring(BEARER_PREFIX.length()));
                Long userId = Long.valueOf(claims.getSubject());
                organisationUserRepository.findWithRolesById(userId)
                        .filter(OrganisationUser::isEnabled)
                        .ifPresent(user -> {
                            Set<Permission> permissions = user.getRoles().stream()
                                    .flatMap(role -> role.getPermissions().stream())
                                    .collect(Collectors.toUnmodifiableSet());
                            OrgUserPrincipal principal = new OrgUserPrincipal(
                                    user.getId(), user.getOrganisation().getId(), permissions);
                            List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                            authorities.add(new SimpleGrantedAuthority("ORG_USER"));
                            permissions.forEach(permission ->
                                    authorities.add(new SimpleGrantedAuthority("PERM_" + permission.name())));
                            UsernamePasswordAuthenticationToken authentication =
                                    new UsernamePasswordAuthenticationToken(principal, null, authorities);
                            SecurityContextHolder.getContext().setAuthentication(authentication);
                        });
            } catch (JwtException | IllegalArgumentException ignored) {
                // leave unauthenticated -> entry point answers 401
            }
        }
        filterChain.doFilter(request, response);
    }
}
