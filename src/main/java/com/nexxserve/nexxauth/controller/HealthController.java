package com.nexxserve.nexxauth.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lightweight, unauthenticated health probe for external uptime monitoring.
 * Returns the DB round-trip time and app version — enough for a status page
 * without exposing internal details.
 */
@RestController
public class HealthController {

    private final DataSource dataSource;
    private final long appStartMs = System.currentTimeMillis();

    @Value("${info.app.version:unknown}")
    private String appVersion;

    public HealthController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("version", appVersion);
        body.put("uptimeMs", System.currentTimeMillis() - appStartMs);

        // DB probe — cheap single-row query + round-trip time
        long dbStart = System.currentTimeMillis();
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT 1")) {
            long dbMs = System.currentTimeMillis() - dbStart;
            body.put("db", Map.of(
                    "status", "UP",
                    "latencyMs", dbMs
            ));
        } catch (Exception e) {
            long dbMs = System.currentTimeMillis() - dbStart;
            body.put("db", Map.of(
                    "status", "DOWN",
                    "latencyMs", dbMs,
                    "error", e.getMessage()
            ));
            body.put("status", "DEGRADED");
            return ResponseEntity.status(503).body(body);
        }

        return ResponseEntity.ok(body);
    }
}
