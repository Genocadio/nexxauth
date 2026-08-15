package com.nexxserve.nexxauth.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * HTTP-level hardening limits ({@code app.http.*}).
 */
@ConfigurationProperties(prefix = "app.http")
public record HttpProperties(long maxBodyBytes) {

    public HttpProperties {
        if (maxBodyBytes <= 0) {
            maxBodyBytes = 65_536;
        }
    }
}
