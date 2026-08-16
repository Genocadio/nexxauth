package com.nexxserve.nexxauth.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Global CORS policy for the platform API. Restricts which browser origins may
 * call the API directly to the allowlist configured via
 * {@code app.cors.allowed-origins} (env {@code CORS_ALLOWED_ORIGINS}) —
 * typically the console's own origin in production.
 * <p>
 * Requests from an organisation client (identified by {@code X-Client-Id}) are
 * deferred to {@link ClientCorsFilter}, which enforces the per-client (org-level)
 * allowlist instead. With an empty allowlist (the default) no global CORS is
 * applied and behaviour is unchanged outside of clients.
 */
@Configuration
public class CorsConfig {

    @Value("${app.cors.allowed-origins:}")
    private String allowedOrigins;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        return request -> {
            // Org-level (per-client) CORS is owned by ClientCorsFilter.
            if (request.getHeader("X-Client-Id") != null || allowedOrigins.isBlank()) {
                return null;
            }
            CorsConfiguration config = new CorsConfiguration();
            config.setAllowedOrigins(Arrays.stream(allowedOrigins.split(","))
                    .map(String::trim)
                    .filter(origin -> !origin.isEmpty())
                    .toList());
            config.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
            config.setAllowedHeaders(List.of("Content-Type", "Authorization", "X-Request-Id"));
            config.setExposedHeaders(List.of("X-Request-Id"));
            config.setMaxAge(3600L);
            return config;
        };
    }
}