package com.nexxserve.nexxauth.security;

import com.nexxserve.nexxauth.entity.LogCategory;
import com.nexxserve.nexxauth.entity.LogLevel;
import com.nexxserve.nexxauth.exception.ErrorResponseWriter;
import com.nexxserve.nexxauth.service.LogService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * Stateless JWT security. Auth endpoints (login/register/refresh/logout) are
 * public; everything else requires a valid access token. Platform write
 * operations additionally require the {@code SUPER_USER} role (both via URL
 * rules here and {@code @PreAuthorize} at method level as defense in depth).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter,
                                                   OrgJwtAuthenticationFilter orgJwtFilter,
                                                   ClientTokenFilter clientTokenFilter,
                                                   OrganisationIdFilter orgIdFilter,
                                                   CorsConfigurationSource corsConfigurationSource,
                                                   ErrorResponseWriter errorResponseWriter,
                                                   LogService logService) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(entryPoint(errorResponseWriter, logService))
                        .accessDeniedHandler(accessDeniedHandler(errorResponseWriter, logService)))
                .authorizeHttpRequests(auth -> auth
                        // Actuator health/info: open so monitoring probes need no token.
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        // Platform (console) auth at the clean root origin.
                        .requestMatchers("/auth/login", "/auth/register",
                                "/auth/refresh", "/auth/logout").permitAll()
                        // Organisation auth (org-level, separate from platform auth).
                        // Platforms live at their own clean root origin: /{slug}/auth/*
                        .requestMatchers("/*/auth/login", "/*/auth/register",
                                "/*/auth/refresh", "/*/auth/logout").permitAll()
                        // Public slug suggestions for the register form (rate
                        // limited per IP); organisation suggestions enforce
                        // authentication in the service.
                        .requestMatchers(HttpMethod.GET, "/slug-suggestions").permitAll()
                        // Public verification keys for an organisation's tokens
                        .requestMatchers(HttpMethod.GET, "/*/organisations/*/keys").permitAll()
                        .requestMatchers(HttpMethod.GET, "/*/organisations/keys").permitAll()
                        // Public documentation context for context-aware API docs
                        .requestMatchers(HttpMethod.GET, "/*/organisations/*/docs/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/*/organisations/docs/**").permitAll()
                        // Organisation endpoints: any authenticated token (platform
                        // or org user); the fine-grained platform-role and
                        // org-permission gating happens at method level.
                        .requestMatchers("/*/organisations/**").authenticated()
                        // Log endpoints (read-only access for platform members).
                        .requestMatchers("/*/logs/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/users/**").hasAnyRole("SUPER_USER", "READ_ONLY")
                        .requestMatchers("/users/**").hasRole("SUPER_USER")
                        // Own-profile endpoints are open to any authenticated
                        // platform user (both roles), so they must be matched
                        // before the platform management rules below.
                        .requestMatchers("/auth/me", "/auth/me/password").authenticated()
                        // Platform management endpoints at the platform's root origin
                        // /{slug} (platform details, platform users, ...).
                        .requestMatchers(HttpMethod.GET, "/*").hasAnyRole("SUPER_USER", "READ_ONLY")
                        .requestMatchers(HttpMethod.GET, "/*/**").hasAnyRole("SUPER_USER", "READ_ONLY")
                        .requestMatchers("/*/**").hasRole("SUPER_USER")
                        .anyRequest().authenticated())
                .addFilterBefore(orgIdFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(orgJwtFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                // Last of the three: a present X-Client-Id always wins over a
                // bearer JWT (see ClientTokenFilter).
                .addFilterBefore(clientTokenFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private org.springframework.security.web.AuthenticationEntryPoint entryPoint(ErrorResponseWriter writer, LogService logService) {
        return (request, response, authException) -> {
            try {
                String path = request.getRequestURI();
                logService.logEvent(LogLevel.WARN, LogCategory.SECURITY,
                        "UNAUTHENTICATED_ACCESS", "Authentication required",
                        null, null, null, "Path: " + path + " Reason: " + authException.getMessage(),
                        null, null);
            } catch (Exception ignored) {
                // Never let logging break the response
            }
            writer.write(response, org.springframework.http.HttpStatus.UNAUTHORIZED,
                    "Authentication required", request.getRequestURI());
        };
    }

    private org.springframework.security.web.access.AccessDeniedHandler accessDeniedHandler(ErrorResponseWriter writer, LogService logService) {
        return (request, response, accessDeniedException) -> {
            try {
                String path = request.getRequestURI();
                logService.logEvent(LogLevel.WARN, LogCategory.SECURITY,
                        "UNAUTHORIZED_ACCESS", "Insufficient permissions",
                        null, null, null, "Path: " + path + " Reason: " + accessDeniedException.getMessage(),
                        null, null);
            } catch (Exception ignored) {
                // Never let logging break the response
            }
            writer.write(response, org.springframework.http.HttpStatus.FORBIDDEN,
                    "Insufficient permissions", request.getRequestURI());
        };
    }
}
