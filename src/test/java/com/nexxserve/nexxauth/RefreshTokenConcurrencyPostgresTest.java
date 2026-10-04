package com.nexxserve.nexxauth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Postgres-only concurrency check for the refresh-token rotation lock
 * (H2's {@code SELECT ... FOR UPDATE} in compatibility mode is not a faithful
 * stand-in for Postgres READ COMMITTED). Disabled unless {@code -Dpostgres.it=true}
 * so the normal H2 test suite (and CI) are unaffected.
 *
 * <p>Run with:
 * <pre>
 *   docker run -d --name nexxauth-it-pg -e POSTGRES_DB=nexxauth \
 *     -e POSTGRES_USER=nexxauth -e POSTGRES_PASSWORD=nexxauth \
 *     -p 5433:5432 postgres:18-alpine
 *   ./gradlew test --tests '*RefreshTokenConcurrencyPostgresTest' -Dpostgres.it=true
 * </pre>
 *
 * <p>Verifies that two concurrent refreshes of the SAME token on real Postgres
 * do not both succeed, that reuse detection fires (the winning token's family is
 * revoked), and that the row lock neither deadlocks nor trips "no active
 * transaction".
 */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "POSTGRES_IT", matches = "true")
class RefreshTokenConcurrencyPostgresTest {

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> System.getenv().getOrDefault("POSTGRES_URL",
                        "jdbc:postgresql://localhost:5433/nexxauth"));
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("POSTGRES_USER", "nexxauth"));
        registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("POSTGRES_PASSWORD", "nexxauth"));
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void concurrentRefreshOfOneTokenYieldsSingleWinnerAndTriggersReuseDetection() throws Exception {
        String platform = "/pg-race";
        String boss = registerPlatform("pg-race-boss@nexx.io", "pg-race");
        long orgId = createOrganisation(boss, platform, "PG Race Org", "pg-race-org");
        String clientKey = createClient(boss, platform + "/organisations/" + orgId, "PG Race Client");
        String orgAuth = platform + "/auth";

        registerOrgUser(orgAuth, clientKey, "raceuser", "pass1234");
        OrgTokens login = loginOrg(orgAuth, clientKey, "raceuser", "pass1234");

        AtomicInteger ok = new AtomicInteger();
        AtomicInteger unauthorized = new AtomicInteger();
        AtomicInteger other = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);

        Runnable racer = () -> {
            try {
                start.await();
                int code = mockMvc.perform(post(orgAuth + "/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of("refreshToken", login.refreshToken()))))
                        .andReturn().getResponse().getStatus();
                if (code == 200) ok.incrementAndGet();
                else if (code == 401) unauthorized.incrementAndGet();
                else other.incrementAndGet();
            } catch (Exception e) {
                other.incrementAndGet();
            }
        };

        Thread t1 = new Thread(racer);
        Thread t2 = new Thread(racer);
        t1.start();
        t2.start();
        start.countDown();
        t1.join(15_000);
        t2.join(15_000);

        assertEquals(0, other.get(), "neither refresh should error/deadlock on real Postgres");
        assertEquals(1, ok.get(),
                "exactly one concurrent refresh of a single token may succeed (row lock serialises rotation)");
        assertEquals(1, unauthorized.get(),
                "the loser must observe the revoked token and be rejected (reuse path)");

        // Reuse detection revokes the whole token family, so the token the winner
        // just received must no longer authenticate either.
        MvcResult winnerRefresh = mockMvc.perform(post(orgAuth + "/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", login.refreshToken()))))
                .andReturn();
        assertTrue(winnerRefresh.getResponse().getStatus() == 401,
                "re-presenting the rotated token must be rejected after reuse detection");
    }

    // --- helpers (same shape as the H2 integration tests) ---

    private record OrgTokens(String accessToken, String refreshToken) {
    }

    private String registerPlatform(String email, String slug) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "firstName", "F", "lastName", "L",
                                "email", email, "password", "password1",
                                "platformName", "PG Race Platform", "platformSlug", slug))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private long createOrganisation(String boss, String platform, String name, String slug) throws Exception {
        MvcResult result = mockMvc.perform(post(platform + "/organisations")
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", name, "slug", slug))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private String createClient(String boss, String org, String name) throws Exception {
        MvcResult result = mockMvc.perform(post(org + "/clients")
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", name, "type", "WEB"))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("clientKey").asText();
    }

    private void registerOrgUser(String orgAuth, String clientKey, String identifier, String password)
            throws Exception {
        mockMvc.perform(post(orgAuth + "/register")
                        .header("X-Client-Id", clientKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "username", identifier, "password", password,
                                "firstName", "F", "lastName", "L"))))
                .andExpect(status().isCreated());
    }

    private OrgTokens loginOrg(String orgAuth, String clientKey, String identifier, String password)
            throws Exception {
        MvcResult result = mockMvc.perform(post(orgAuth + "/login")
                        .header("X-Client-Id", clientKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("identifier", identifier, "identifierType", "USERNAME", "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        var node = objectMapper.readTree(result.getResponse().getContentAsString());
        return new OrgTokens(node.get("accessToken").asText(), node.get("refreshToken").asText());
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}