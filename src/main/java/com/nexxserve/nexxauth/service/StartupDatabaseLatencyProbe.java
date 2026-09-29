package com.nexxserve.nexxauth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Measures database round-trip latency at startup and logs one summary line.
 *
 * <p>Runs {@value #DEFAULT_ATTEMPTS} probes of {@code SELECT 1} on separate
 * connections after the context is up, reporting per-attempt and aggregate
 * numbers (min/median/max) plus the pool's own view. The point is to make the
 * app→DB hop visible in the boot log: a single slow deployment tells you
 * whether latency lives in the network path or in the database itself, which a
 * later request-level number cannot distinguish.
 *
 * <p>Readiness does not depend on this. Every failure mode is caught and logged,
 * never rethrown — a probe that cannot reach the database must not turn a
 * running app into a crash loop, and Flyway has already proved the schema is
 * reachable by the time any runner executes.
 *
 * <p>Deliberately uses its own connections rather than the pool: probing through
 * a borrowed pool connection would report cache-warm latency and hide the cost
 * of establishing a connection from scratch.
 */
@Component
@Order(10)
public class StartupDatabaseLatencyProbe implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupDatabaseLatencyProbe.class);

    /** Probes to run. 3-5 separates a one-off DNS/TLS handshake from steady state. */
    public static final int DEFAULT_ATTEMPTS = 5;

    /** Gap between probes so each one is a fresh connection, not a reused one. */
    private static final long INTER_PROBE_SLEEP_MS = 150L;

    private final DataSource dataSource;
    private final int attempts;

    public StartupDatabaseLatencyProbe(DataSource dataSource) {
        this.dataSource = dataSource;
        this.attempts = DEFAULT_ATTEMPTS;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<Long> samples = new ArrayList<>(attempts);
        int failures = 0;
        String lastError = null;

        log.info("database latency probe starting: attempts={}", attempts);

        for (int i = 1; i <= attempts; i++) {
            try {
                long ms = probeOnce();
                samples.add(ms);
                log.info("database latency probe {}/{}: {}ms", i, attempts, ms);
            } catch (Exception e) {
                failures++;
                lastError = e.getClass().getSimpleName() + ": " + e.getMessage();
                log.warn("database latency probe {}/{} failed: {}", i, attempts, lastError);
            }
            if (i < attempts) {
                sleepQuietly();
            }
        }

        if (samples.isEmpty()) {
            log.warn("database latency probe complete: no successful probe ({}/{} failed), last error: {}",
                    failures, attempts, lastError);
            return;
        }

        List<Long> sorted = new ArrayList<>(samples);
        sorted.sort(null);
        long min = sorted.get(0);
        long max = sorted.get(sorted.size() - 1);
        // Middle element of an odd count; for an even count this is the lower
        // middle, which is fine for a startup diagnostic.
        long median = sorted.get(sorted.size() / 2);

        log.info("database latency probe complete: db={} attempts={} ok={} failed={} min={}ms median={}ms max={}ms samples={}",
                describeTarget(), attempts, samples.size(), failures, min, median, max, samples);

        if (failures > 0) {
            log.warn("database latency probe had {} failed probe(s); last error: {}", failures, lastError);
        }
    }

    /**
     * One round trip on a brand-new connection: the pool connection timeout is
     * not overridden, so a dead database surfaces as this attempt failing
     * rather than hanging the boot sequence.
     */
    private long probeOnce() throws Exception {
        long start = System.nanoTime();
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT 1")) {
            // Consume the row so the measurement covers the full server round trip
            // rather than just statement dispatch.
            rs.next();
            return Math.round((System.nanoTime() - start) / 1_000_000.0);
        }
    }

    private String describeTarget() {
        try (Connection conn = dataSource.getConnection()) {
            String meta = conn.getMetaData().getURL();
            return meta == null ? "unknown" : redactCredentials(meta);
        } catch (Exception e) {
            // Not fatal: the probe results are the useful part, and the host is
            // already visible in the JDBC URL in config.
            return "unknown";
        }
    }

    /** Strips any {@code user=}/{@code password=} pair so credentials never reach the log. */
    private static String redactCredentials(String jdbcUrl) {
        return jdbcUrl.replaceAll("(?i)(password|user)=[^&;]*", "$1=***");
    }

    private void sleepQuietly() {
        try {
            Thread.sleep(INTER_PROBE_SLEEP_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
