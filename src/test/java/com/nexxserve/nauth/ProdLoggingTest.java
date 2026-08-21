package com.nexxserve.nauth;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots the real {@code prod} profile and proves the production logging config:
 * ECS-structured JSON on the console and in the rotating file appender, with the
 * MDC {@code requestId} (set by {@code RequestIdFilter} on every request)
 * included in every event.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("prod")
class ProdLoggingTest {

    // Unique per run: the file appender opens its target at context startup, so
    // reusing a path across runs (or deleting the file mid-test) would read stale
    // or orphaned content.
    static final String LOG_FILE =
            "build/test-output/nauth-prod-" + UUID.randomUUID().toString().substring(0, 8) + ".log";

    private static final Logger log = LoggerFactory.getLogger(ProdLoggingTest.class);

    static {
        // The logging system initializes before @SpringBootTest inline properties
        // are applied, so point the file appender at a build-local path via a
        // system property (visible from the very start of the environment).
        System.setProperty("logging.file.name", LOG_FILE);
        try {
            Files.createDirectories(Path.of(LOG_FILE).toAbsolutePath().getParent());
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @AfterAll
    static void cleanup() {
        System.clearProperty("logging.file.name");
    }

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private Environment environment;

    @Test
    void prodProfileWritesStructuredJsonWithRequestId() throws Exception {
        // The prod profile config is what the app will actually run with.
        assertThat(environment.getProperty("logging.structured.format.console")).isEqualTo("ecs");
        assertThat(environment.getProperty("logging.structured.format.file")).isEqualTo("ecs");
        assertThat(environment.getProperty("logging.structured.ecs.service.name")).isEqualTo("nauth");

        Path logPath = Path.of(LOG_FILE).toAbsolutePath();

        MDC.put("requestId", "test-request-123");
        log.info("prod logging verification");
        MDC.remove("requestId");

        // The file appender flushes per event; a short retry guards against timing.
        List<String> lines = List.of();
        for (int i = 0; i < 20 && lines.isEmpty(); i++) {
            Thread.sleep(100);
            if (Files.exists(logPath)) {
                lines = Files.readAllLines(logPath);
            }
        }
        List<String> captured = lines;
        assertThat(captured).as("expected at least one structured log line in %s", logPath).isNotEmpty();

        String line = captured.stream()
                .filter(l -> l.contains("test-request-123"))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "no log line contained requestId; got:\n" + String.join("\n", captured)));

        // The line is a single ECS JSON document with the expected fields.
        JsonNode node = objectMapper.readTree(line);
        assertThat(node.has("@timestamp")).isTrue();
        assertThat(node.path("log").path("level").asText()).isEqualTo("INFO");
        assertThat(node.path("message").asText()).isEqualTo("prod logging verification");
        assertThat(node.path("service").path("name").asText()).isEqualTo("nauth");

        // requestId from the MDC made it into the structured output.
        JsonNode requestId = node.get("requestId");
        assertThat(requestId).as("requestId missing from: %s", line).isNotNull();
        assertThat(requestId.asText()).isEqualTo("test-request-123");
    }
}
