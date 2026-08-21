package com.nexxserve.nauth.security;

import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Applies the configured per-IP token buckets to credential endpoints. The
 * actual buckets live in a {@link RateLimitStore} chosen by configuration
 * (in-memory for a single instance, Redis for horizontal scaling).
 */
@Service
public class RateLimitService {

    private final RateLimitProperties properties;
    private final RateLimitStore store;

    public RateLimitService(RateLimitProperties properties, RateLimitStore store) {
        this.properties = properties;
        this.store = store;
    }

    /**
     * Attempts to consume one token for {@code key}. Returns the number of
     * seconds the caller must wait when the bucket is empty, or empty when the
     * request is allowed (or rate limiting is disabled).
     */
    public Optional<Long> tryConsume(String key, RateLimitProperties.Limit limit) {
        if (!properties.isEnabled()) {
            return Optional.empty();
        }
        return store.tryConsume(key, limit);
    }
}
