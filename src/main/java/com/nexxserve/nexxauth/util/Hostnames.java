package com.nexxserve.nexxauth.util;

import jakarta.servlet.http.HttpServletRequest;

import java.net.InetAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Resolves a human-readable source domain for the client that initiated a
 * request. Two strategies, in order of usefulness for the session UI:
 * <ol>
 *   <li><b>Origin header</b> — browser/web clients send an {@code Origin}
 *       ({@code https://app.example.com}); this is the true source domain and
 *       is returned as-is.</li>
 *   <li><b>Reverse DNS</b> — for non-browser (server/mobile) clients the IP has
 *       no Origin, so we attempt a reverse lookup of the resolved client IP.
 *       Rarely useful for residential/mobile IPs (no PTR record), and here the
 *       hostname returned by {@code getHostName()} falls back to the IP itself,
 *       so callers can't tell a real hostname from a failed lookup — we guard
 *       against that by only returning a value when it actually differs from
 *       the IP.</li>
 * </ol>
 * Reverse DNS may take up to a few seconds on a slow resolver, so it runs off
 * the request thread on a bounded shared executor and never throws. Use
 * {@link #resolveBlocking} only when you accept the latency (e.g. login).
 */
public final class Hostnames {

    private static final ExecutorService LOOKUP_POOL =
            Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "rdns-lookup");
                t.setDaemon(true);
                return t;
            });

    private Hostnames() {
    }

    /**
     * Best-effort async reverse lookup. Returns a {@link CompletableFuture}
     * that completes with the hostname (or {@code null}) once resolved.
     */
    public static CompletableFuture<String> reverseLookup(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.supplyAsync(() -> reverseLookupSync(ipAddress), LOOKUP_POOL)
                .orTimeout(3, TimeUnit.SECONDS)
                .exceptionally(ex -> null);
    }

    /** Blocking reverse lookup with the 3s cap, or null on failure. */
    public static String reverseLookupSync(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) return null;
        try {
            InetAddress addr = InetAddress.getByName(ipAddress);
            String host = addr.getHostName();
            // getHostName() returns the IP literal when lookup fails — treat
            // that as "no hostname" so callers can rely on a non-null result.
            if (host == null || host.equals(ipAddress) || host.isEmpty()) return null;
            return host;
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * Resolve the source domain for this request: prefer the {@code Origin}
     * header when present, else the reverse-DNS hostname of the client IP.
     * Accepts the already-resolved IP to avoid re-resolving it here.
     */
    public static String resolve(HttpServletRequest request, String resolvedIp) {
        String origin = request.getHeader("Origin");
        if (origin != null && !origin.isBlank()) {
            // Strip protocol and trailing slash -> bare hostname
            String cleaned = origin.replaceFirst("^[a-zA-Z][a-zA-Z0-9+.-]*://", "");
            cleaned = cleaned.replaceFirst("/.*$", "");
            cleaned = cleaned.trim();
            if (!cleaned.isEmpty()) return cleaned;
        }
        return reverseLookupSync(resolvedIp);
    }

    /**
     * Non-blocking variant: returns the Origin-derived domain immediately, and
     * kicks off the reverse-DNS lookup in the background. The caller should map
     * the returned future to null to keep the Origin fast.
     */
    public static String resolveOriginOnly(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (origin != null && !origin.isBlank()) {
            String cleaned = origin.replaceFirst("^[a-zA-Z][a-zA-Z0-9+.-]*://", "");
            cleaned = cleaned.replaceFirst("/.*$", "");
            cleaned = cleaned.trim();
            if (!cleaned.isEmpty()) return cleaned;
        }
        return null;
    }
}
