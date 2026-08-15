package com.nexxserve.nexxauth.security;

import com.nexxserve.nexxauth.exception.ErrorResponseWriter;
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
                                                   ErrorResponseWriter errorResponseWriter) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(entryPoint(errorResponseWriter))
                        .accessDeniedHandler(accessDeniedHandler(errorResponseWriter)))
                .authorizeHttpRequests(auth -> auth
                        // Actuator health/info: served on the separate management
                        // port (8081), open so monitoring probes need no token.
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/api/v1/auth/login", "/api/v1/auth/register",
                                "/api/v1/auth/refresh", "/api/v1/auth/logout").permitAll()
                        // Organisation auth (org-level, separate from platform auth)
                        .requestMatchers("/api/v1/platforms/*/auth/login", "/api/v1/platforms/*/auth/register",
                                "/api/v1/platforms/*/auth/refresh", "/api/v1/platforms/*/auth/logout").permitAll()
                        // Public slug suggestions for the register form (rate
                        // limited per IP); organisation suggestions enforce
                        // authentication in the service.
                        .requestMatchers(HttpMethod.GET, "/api/v1/slug-suggestions").permitAll()
                        // Public verification keys for an organisation's tokens
                        .requestMatchers(HttpMethod.GET, "/api/v1/platforms/*/organisations/*/keys").permitAll()
                        // Organisation endpoints: any authenticated token (platform
                        // or org user); the fine-grained platform-role and
                        // org-permission gating happens at method level.
                        .requestMatchers("/api/v1/platforms/*/organisations/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/platforms/**").hasAnyRole("SUPER_USER", "READ_ONLY")
                        .requestMatchers("/api/v1/platforms/**").hasRole("SUPER_USER")
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/**").hasAnyRole("SUPER_USER", "READ_ONLY")
                        .requestMatchers("/api/v1/users/**").hasRole("SUPER_USER")
                        .anyRequest().authenticated())
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

    private org.springframework.security.web.AuthenticationEntryPoint entryPoint(ErrorResponseWriter writer) {
        return (request, response, authException) -> writer.write(response,
                org.springframework.http.HttpStatus.UNAUTHORIZED, "Authentication required", request.getRequestURI());
    }

    private org.springframework.security.web.access.AccessDeniedHandler accessDeniedHandler(ErrorResponseWriter writer) {
        return (request, response, accessDeniedException) -> writer.write(response,
                org.springframework.http.HttpStatus.FORBIDDEN, "Insufficient permissions", request.getRequestURI());
    }
}
