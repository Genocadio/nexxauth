package com.nexxserve.nexxauth.security;

import java.util.Optional;

/**
 * Backing store for the per-IP token buckets. Two implementations exist: an
 * in-memory Caffeine store (default, single instance) and a shared Redis store
 * (bucket4j over Lettuce) for horizontally scaled deployments. Backends must
 * never throw for a request to be allowed: the limiter is a defense-in-depth
 * throttle, not a correctness gate, so a store failure should fail open.
 */
public interface RateLimitStore {

    /**
     * Attempts to consume one token for {@code key} using {@code limit}.
     * Returns the number of seconds the caller must wait when the bucket is
     * empty, or empty when the request is allowed (or limiting is disabled).
     */
    Optional<Long> tryConsume(String key, RateLimitProperties.Limit limit);
}
