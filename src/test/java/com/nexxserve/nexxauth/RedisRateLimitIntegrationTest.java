package com.nexxserve.nexxauth;

import com.nexxserve.nexxauth.security.RedisRateLimitStore;
import com.nexxserve.nexxauth.security.RateLimitStore;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the Redis-backed rate-limit store end to end — the shared-store path
 * used for horizontal scaling. Requires a live Redis (localhost:6379 by
 * default; override {@code app.rate-limit.redis.*}). Tagged {@code redis} and
 * excluded from the default test run: run it with
 * {@code ./gradlew test -PincludeRedisTests} against a running Redis (CI runs
 * this against the Redis service). Proves the store bean is actually the Redis
 * implementation and that bucket enforcement (429 + {@code Retry-After}) works
 * over it, not just the in-memory default.
 */
@Tag("redis")
@SpringBootTest(properties = {
        "app.rate-limit.store=redis",
        "app.rate-limit.redis.host=localhost",
        "app.rate-limit.redis.port=6379",
        "app.rate-limit.login.capacity=2",
        "app.rate-limit.login.refill-per-minute=1",
})
@AutoConfigureMockMvc
class RedisRateLimitIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RateLimitStore store;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void redisStoreIsTheSelectedImplementation() {
        assertThat(store).isInstanceOf(RedisRateLimitStore.class);
    }

    @Test
    void loginIsLimitedPerIpOverRedis() throws Exception {
        String body = json(Map.of("email", "nobody@example.com", "password", "wrong-password"));

        // First two attempts are processed (rejected for bad credentials, not throttled)...
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());

        // ...the third is throttled with the unified error shape, backed by Redis.
        MvcResult blocked = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.path").value("/auth/login"))
                .andReturn();

        assertThat(blocked.getResponse().getHeader("Retry-After")).matches("\\d+");
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
