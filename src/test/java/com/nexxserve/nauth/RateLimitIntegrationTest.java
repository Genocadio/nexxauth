package com.nexxserve.nauth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the per-IP token bucket on the credential endpoints: allowed
 * requests pass, then excess attempts get a 429 with a {@code Retry-After}
 * header and the unified error body. This test runs in its own context (tiny
 * limits) so the shared test context is unaffected.
 */
@SpringBootTest(properties = {
        "app.rate-limit.enabled=true",
        "app.rate-limit.login.capacity=2",
        "app.rate-limit.login.refill-per-minute=1",
        "app.rate-limit.register.capacity=2",
        "app.rate-limit.register.refill-per-minute=1",
        "app.rate-limit.refresh.capacity=2",
        "app.rate-limit.refresh.refill-per-minute=1",
        "app.rate-limit.suggestions.capacity=2",
        "app.rate-limit.suggestions.refill-per-minute=1"
})
@AutoConfigureMockMvc
class RateLimitIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void loginIsLimitedPerIp() throws Exception {
        String body = json(Map.of("email", "nobody@example.com", "password", "wrong-password"));

        // First two attempts are processed (rejected for bad credentials, not throttled)...
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());

        // ...the third is throttled with the unified error shape.
        MvcResult blocked = mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.path").value("/auth/login"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andReturn();

        JsonNode error = objectMapper.readTree(blocked.getResponse().getContentAsString());
        assertThat(error.get("error").asText()).isEqualTo("Too Many Requests");
        assertThat(error.get("message").asText()).isNotBlank();
        assertThat(blocked.getResponse().getHeader("Retry-After")).matches("\\d+");
    }

    @Test
    void registerIsLimitedPerIp() throws Exception {
        register("first@example.com").andExpect(status().isCreated());
        register("second@example.com").andExpect(status().isCreated());

        register("third@example.com")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.path").value("/auth/register"));
    }

    @Test
    void refreshIsLimitedPerIp() throws Exception {
        String body = json(Map.of("refreshToken", "garbage-token"));

        // First two refresh attempts are processed (rejected for an unknown
        // token, not throttled)...
        mockMvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());

        // ...the third is throttled with the unified error shape.
        mockMvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.path").value("/auth/refresh"));
    }

    @Test
    void orgRefreshHasItsOwnBucket() throws Exception {
        String body = json(Map.of("refreshToken", "garbage-token"));
        String path = "/some-slug/auth/refresh";

        // Org refresh is limited independently from the platform refresh
        // bucket (org-* key): even if the platform bucket is exhausted, org
        // refresh still gets its own 2 attempts.
        mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.path").value(path));
    }

    @Test
    void slugSuggestionsAreLimitedPerIp() throws Exception {
        String url = "/slug-suggestions?type=PLATFORM&name=Acme";

        // First two lookups are processed...
        mockMvc.perform(get(url)).andExpect(status().isOk());
        mockMvc.perform(get(url)).andExpect(status().isOk());

        // ...the third is throttled with the unified error shape.
        mockMvc.perform(get(url))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.path").value("/slug-suggestions"));
    }

    private org.springframework.test.web.servlet.ResultActions register(String email) throws Exception {
        return mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "firstName", "A",
                        "lastName", "B",
                        "email", email,
                        "password", "password1",
                        "platformName", "RL Corp " + email))));
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
