package com.nexxserve.nexxauth.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Per-account brute-force protection. After {@link #MAX_FAILURES} failed login
 * attempts within {@link #LOCKOUT_WINDOW_MS}, the account is locked for
 * {@link #LOCKOUT_DURATION_MS}. This complements the per-IP rate limiter: a
 * distributed attack from many IPs against a single account is still caught.
 * <p>
 * The counter is stored in a Caffeine cache (keyed by {@code orgId:identifier})
 * with a sliding window — the entry expires after the lockout window, resetting
 * the counter. The lockout flag is set when the threshold is reached and expires
 * after the lockout duration.
 * <p>
 * This is an in-memory, best-effort mechanism. For a clustered deployment,
 * the Redis rate-limit store could be extended to support this, but the
 * per-IP rate limiter already handles most distributed scenarios.
 */
@Component
public class AccountLockoutService {

    /** Number of failures before lockout. */
    private static final int MAX_FAILURES = 10;

    /** Window (ms) in which failures are counted. */
    private static final long LOCKOUT_WINDOW_MS = TimeUnit.MINUTES.toMillis(15);

    /** Duration (ms) the account stays locked. */
    private static final long LOCKOUT_DURATION_MS = TimeUnit.MINUTES.toMillis(15);

    /** Failure counter: key = "orgId:identifier", value = count. */
    private final Cache<String, AtomicInteger> failureCounts = Caffeine.newBuilder()
            .expireAfterWrite(LOCKOUT_WINDOW_MS, TimeUnit.MILLISECONDS)
            .maximumSize(10_000)
            .build();

    /** Lockout flags: key = "orgId:identifier", value = true. */
    private final Cache<String, Boolean> lockouts = Caffeine.newBuilder()
            .expireAfterWrite(LOCKOUT_DURATION_MS, TimeUnit.MILLISECONDS)
            .maximumSize(10_000)
            .build();

    /**
     * Record a failed login attempt. Returns {@code true} if the account
     * is now locked (this was the threshold-crossing attempt).
     */
    public boolean recordFailure(Long orgId, String identifier) {
        String key = orgId + ":" + identifier;
        AtomicInteger count = failureCounts.get(key, k -> new AtomicInteger(0));
        int current = count.incrementAndGet();
        if (current >= MAX_FAILURES) {
            lockouts.put(key, Boolean.TRUE);
            return true;
        }
        return false;
    }

    /**
     * Check if the account is currently locked. Returns {@code true} if
     * the account has exceeded the failure threshold and is within the
     * lockout window.
     */
    public boolean isLocked(Long orgId, String identifier) {
        String key = orgId + ":" + identifier;
        return lockouts.getIfPresent(key) != null;
    }

    /**
     * Clear the failure counter for an account (e.g. after a successful login).
     */
    public void clearFailures(Long orgId, String identifier) {
        String key = orgId + ":" + identifier;
        failureCounts.invalidate(key);
        lockouts.invalidate(key);
    }

    /** Remaining lockout seconds for display to the user. Returns 0 if not locked. */
    public long remainingLockoutSeconds(Long orgId, String identifier) {
        String key = orgId + ":" + identifier;
        var entry = lockouts.asMap().get(key);
        if (entry == null) return 0;
        // Caffeine doesn't expose per-entry TTL; approximate from window size
        return LOCKOUT_DURATION_MS / 1000;
    }
}
