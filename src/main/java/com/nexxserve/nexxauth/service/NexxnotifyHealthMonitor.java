package com.nexxserve.nexxauth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically checks that the nexxnotify (nexxbotify) service is reachable
 * and healthy. The check hits {@code GET /healthz} which is always public
 * (no API key required), so it works even when the API key is rotated.
 *
 * <p>Results are logged at INFO (healthy) or WARN (unhealthy / unconfigured).
 * This makes it easy to wire into alerting via log-based monitors.
 */
@Component
public class NexxnotifyHealthMonitor {

    private static final Logger log = LoggerFactory.getLogger(NexxnotifyHealthMonitor.class);

    private final NexxbotifyClient nexxbotifyClient;
    private final NexxbotifyProperties properties;

    public NexxnotifyHealthMonitor(NexxbotifyClient nexxbotifyClient, NexxbotifyProperties properties) {
        this.nexxbotifyClient = nexxbotifyClient;
        this.properties = properties;
    }

    /**
     * Runs every hour (at :00) and on startup after a 30-second delay.
     * Logs the health status of the nexxnotify service.
     */
    @Scheduled(fixedRate = 3_600_000, initialDelay = 30_000)
    public void checkNexxnotifyHealth() {
        if (!nexxbotifyClient.isConfigured()) {
            log.debug("nexxnotify health check skipped: NEXXNOTIFY_URL is not configured");
            return;
        }
        String url = properties.getBaseUrl();
        boolean healthy = nexxbotifyClient.checkHealth();
        if (healthy) {
            log.info("nexxnotify health check OK [url={}]", url);
        } else {
            log.warn("nexxnotify health check FAILED [url={}] — notification delivery may be impaired", url);
        }
    }
}
