package com.nexxserve.nexxauth.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * In-memory token-bucket store: buckets live in a bounded, expiring Caffeine
 * cache. Correct for a single instance; a shared store ({@code store=redis})
 * must be used when the service is scaled horizontally so limits hold across
 * instances.
 */
public class InMemoryRateLimitStore implements RateLimitStore {

    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(1, TimeUnit.HOURS)
            .maximumSize(100_000)
            .build();

    @Override
    public Optional<Long> tryConsume(String key, RateLimitProperties.Limit limit) {
        Bucket bucket = buckets.get(key, ignored -> newBucket(limit));
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            return Optional.empty();
        }
        long waitSeconds = probe.getNanosToWaitForRefill() / 1_000_000_000L;
        return Optional.of(Math.max(1, waitSeconds));
    }

    private Bucket newBucket(RateLimitProperties.Limit limit) {
        return Bucket.builder()
                .addLimit(Bandwidth.classic(limit.getCapacity(),
                        Refill.greedy(limit.getRefillPerMinute(), Duration.ofMinutes(1))))
                .build();
    }
}
