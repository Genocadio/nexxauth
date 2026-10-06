package com.nexxserve.nexxauth.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.stream.Stream;

/**
 * Browser origins allowed to call the platform API directly
 * ({@code app.cors.*}). Populated from {@code APP_CORS_FRONTEND_ORIGIN},
 * {@code APP_CORS_BACKEND_ORIGIN} and {@code APP_CORS_EXTRA_ORIGINS} via
 * Spring Boot's relaxed environment-variable binding (an {@code APP_CORS_*}
 * variable binds to the matching {@code app.cors.*} key directly, so no
 * placeholder indirection is required).
 */
@Component
@ConfigurationProperties(prefix = "app.cors")
public class CorsProperties {

    /** The console origin (the browser app that calls the API). */
    private String frontendOrigin;

    /** The backend's own public origin. */
    private String backendOrigin;

    /** Comma-separated additional origins, treated like the frontend origin. */
    private String extraOrigins;

    public String getFrontendOrigin() {
        return frontendOrigin;
    }

    public void setFrontendOrigin(String frontendOrigin) {
        this.frontendOrigin = frontendOrigin;
    }

    public String getBackendOrigin() {
        return backendOrigin;
    }

    public void setBackendOrigin(String backendOrigin) {
        this.backendOrigin = backendOrigin;
    }

    public String getExtraOrigins() {
        return extraOrigins;
    }

    public void setExtraOrigins(String extraOrigins) {
        this.extraOrigins = extraOrigins;
    }

    /** The effective allowlist: frontend + backend + extras, de-duplicated. */
    public java.util.List<String> allowedOrigins() {
        return Stream.of(frontendOrigin, backendOrigin, extraOrigins)
                .filter(origin -> origin != null)
                .flatMap(origin -> Stream.of(origin.split(",")))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .distinct()
                .toList();
    }
}