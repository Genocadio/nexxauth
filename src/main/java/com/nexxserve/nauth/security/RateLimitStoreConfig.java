package com.nexxserve.nauth.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Picks the rate-limit store from {@code app.rate-limit.store}: the bounded
 * in-memory Caffeine store by default, or the shared Redis store for
 * multi-instance deployments.
 */
@Configuration
public class RateLimitStoreConfig {

    @Bean
    @ConditionalOnProperty(name = "app.rate-limit.store", havingValue = "in-memory", matchIfMissing = true)
    public RateLimitStore inMemoryRateLimitStore() {
        return new InMemoryRateLimitStore();
    }

    @Bean
    @ConditionalOnProperty(name = "app.rate-limit.store", havingValue = "redis")
    public RateLimitStore redisRateLimitStore(RateLimitProperties properties) {
        return new RedisRateLimitStore(properties.getRedis());
    }
}
