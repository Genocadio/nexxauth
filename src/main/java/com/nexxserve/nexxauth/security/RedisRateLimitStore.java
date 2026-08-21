package com.nexxserve.nexxauth.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.api.StatefulRedisConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

/**
 * Shared token-bucket store backed by Redis (bucket4j CAS manager over
 * Lettuce). Used when {@code app.rate-limit.store=redis} so limits hold across
 * every instance. Deliberately fails open: if Redis is unreachable the request
 * is allowed (with a warning) rather than auth being knocked out wholesale.
 */
public class RedisRateLimitStore implements RateLimitStore {

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimitStore.class);

    private final LettuceBasedProxyManager<byte[]> proxyManager;

    public RedisRateLimitStore(RateLimitProperties.Redis redis) {
        RedisURI.Builder builder = RedisURI.builder()
                .withHost(redis.getHost())
                .withPort(redis.getPort());
        if (redis.getPassword() != null && !redis.getPassword().isEmpty()) {
            builder.withPassword(redis.getPassword());
        }
        if (redis.isSsl()) {
            builder.withSsl(true);
        }
        RedisClient client = RedisClient.create(builder.build());
        StatefulRedisConnection<byte[], byte[]> connection = client.connect(new ByteArrayCodec());
        this.proxyManager = LettuceBasedProxyManager.builderFor(connection).build();
    }

    @Override
    public Optional<Long> tryConsume(String key, RateLimitProperties.Limit limit) {
        try {
            Bucket bucket = proxyManager.getProxy(key.getBytes(StandardCharsets.UTF_8), () -> toConfiguration(limit));
            ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
            if (probe.isConsumed()) {
                return Optional.empty();
            }
            long waitSeconds = probe.getNanosToWaitForRefill() / 1_000_000_000L;
            return Optional.of(Math.max(1, waitSeconds));
        } catch (RuntimeException e) {
            log.warn("Rate limit store unavailable for key '{}'; allowing request (fail-open)", key, e);
            return Optional.empty();
        }
    }

    private BucketConfiguration toConfiguration(RateLimitProperties.Limit limit) {
        return BucketConfiguration.builder()
                .addLimit(Bandwidth.classic(limit.getCapacity(),
                        Refill.greedy(limit.getRefillPerMinute(), Duration.ofMinutes(1))))
                .build();
    }
}
