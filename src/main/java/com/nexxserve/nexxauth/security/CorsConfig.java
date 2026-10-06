package com.nexxserve.nexxauth.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.List;

/**
 * Global CORS policy for the platform API. The browser origins allowed to call
 * the API directly are: the console origin, the backend's own origin, plus any
 * extra origins, all from {@link CorsProperties} ({@code app.cors.*}).
 * <p>
 * Requests from an organisation client (identified by {@code X-Client-Id}) are
 * deferred to {@link ClientCorsFilter}, which enforces the per-client (org-level)
 * allowlist instead. With no origins configured, no global CORS is applied and
 * behaviour is unchanged outside of clients.
 */
@Configuration
public class CorsConfig {

    private static final Logger log = LoggerFactory.getLogger(CorsConfig.class);

    private final CorsProperties properties;

    public CorsConfig(CorsProperties properties) {
        this.properties = properties;
    }

    // @Primary: Spring MVC also contributes a CorsConfigurationSource bean
    // (mvcHandlerMappingIntrospector). Without this, injecting
    // CorsConfigurationSource by type is ambiguous, and the security filter
    // chain can end up wired to MVC's introspector instead of this policy.
    @Bean
    @Primary
    public CorsConfigurationSource corsConfigurationSource() {
        List<String> origins = properties.allowedOrigins();
        // Logged once at startup: an allowlist that fails to bind leaves the API
        // rejecting every browser Origin with "Invalid CORS request", which is
        // otherwise silent and very hard to diagnose.
        if (origins.isEmpty()) {
            log.warn("No global CORS origins configured (app.cors.frontend-origin / "
                    + "app.cors.backend-origin / app.cors.extra-origins are all empty). "
                    + "Browser requests carrying an Origin header will be rejected with "
                    + "HTTP 403 'Invalid CORS request'. Set APP_CORS_FRONTEND_ORIGIN and "
                    + "APP_CORS_BACKEND_ORIGIN if direct browser access is expected.");
        } else {
            log.info("Global CORS allowed origins: {}", origins);
        }
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
}