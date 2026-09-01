package com.nexxserve.nexxauth.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.nexxserve.nexxauth.entity.OrganisationClient;
import com.nexxserve.nexxauth.repository.OrganisationClientRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Short-lived Caffeine cache for {@code X-Client-Id → OrganisationClient}
 * lookups. Both {@link OrganisationIdFilter} (URL rewriting) and
 * {@link ClientTokenFilter} (auth enforcement) need the same client entity
 * on every request that carries an {@code X-Client-Id}. Without this cache
 * each request hits the database twice for the same row.
 * <p>
 * A 30-second TTL keeps the cache fresh enough for client config changes
 * (enable/disable, token rotation) while eliminating the redundant query
 * on every request. The cache is small (max 500 entries) because the
 * number of active clients is bounded by design.
 */
@Component
public class ClientCache {

    /** Null-safe cache: absent keys return Optional.empty (cached as misses). */
    private final Cache<String, Optional<OrganisationClient>> cache = Caffeine.newBuilder()
            .expireAfterWrite(30, TimeUnit.SECONDS)
            .maximumSize(500)
            .build();

    private final OrganisationClientRepository clientRepository;

    public ClientCache(OrganisationClientRepository clientRepository) {
        this.clientRepository = clientRepository;
    }

    /**
     * Resolve a client by its key, using the cache. Returns
     * {@code Optional.empty()} for unknown keys (cached as misses for 30s
     * to prevent key-flooding amplifying into DB queries).
     */
    public Optional<OrganisationClient> findByClientKey(String clientKey) {
        return cache.get(clientKey.trim(),
                key -> clientRepository.findByClientKey(key));
    }

    /**
     * Invalidate a specific client entry (e.g. after token rotation or
     * enable/disable). Safe to call with unknown keys.
     */
    public void invalidate(String clientKey) {
        if (clientKey != null) {
            cache.invalidate(clientKey.trim());
        }
    }

    /** Invalidate the entire cache (e.g. after a bulk client operation). */
    public void invalidateAll() {
        cache.invalidateAll();
    }
}
