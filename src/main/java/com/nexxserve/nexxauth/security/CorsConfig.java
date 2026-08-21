package com.nexxserve.nexxauth.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.List;
import java.util.stream.Stream;

/**
 * Global CORS policy for the platform API. The browser origins allowed to call
 * the API directly are: the console origin ({@code app.cors.frontend-origin}),
 * the backend's own origin ({@code app.cors.backend-origin}), plus any extra
 * origins in {@code app.cors.extra-origins} (comma-separated) which are treated
 * exactly like the frontend origin.
 * <p>
 * Requests from an organisation client (identified by {@code X-Client-Id}) are
 * deferred to {@link ClientCorsFilter}, which enforces the per-client (org-level)
 * allowlist instead. With no origins configured, no global CORS is applied and
 * behaviour is unchanged outside of clients.
 */
@Configuration
public class CorsConfig {

    @Value("${app.cors.frontend-origin:}")
    private String frontendOrigin;

    @Value("${app.cors.backend-origin:}")
    private String backendOrigin;

    @Value("${app.cors.extra-origins:}")
    private String extraOrigins;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        List<String> origins = allowedOrigins();
        return request -> {
            // Org-level (per-client) CORS is owned by ClientCorsFilter.
            if (request.getHeader("X-Client-Id") != null || origins.isEmpty()) {
                return null;
            }
            CorsConfiguration config = new CorsConfiguration();
            config.setAllowedOrigins(origins);
            config.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
            config.setAllowedHeaders(List.of("Content-Type", "Authorization", "X-Request-Id"));
            config.setExposedHeaders(List.of("X-Request-Id"));
            config.setMaxAge(3600L);
            return config;
        };
    }

    private List<String> allowedOrigins() {
        return Stream.of(frontendOrigin, backendOrigin, extraOrigins)
                .filter(origin -> origin != null)
                .flatMap(origin -> Stream.of(origin.split(",")))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .distinct()
                .toList();
    }
}