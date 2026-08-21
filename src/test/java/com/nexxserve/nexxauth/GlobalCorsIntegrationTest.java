package com.nexxserve.nexxauth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Global CORS allowlist: frontend origin + backend origin + extra origins
 * ({@code app.cors.frontend-origin} / {@code app.cors.backend-origin} /
 * {@code app.cors.extra-origins}). Complements the org-level per-client CORS
 * enforced by {@code ClientCorsFilter}: non-client browser origins are
 * restricted to the configured list.
 */
@SpringBootTest(properties = {
        "app.cors.frontend-origin=https://app.example.com",
        "app.cors.backend-origin=https://api.example.com",
        "app.cors.extra-origins=https://portal.example.com, https://third.example.com"
})
@AutoConfigureMockMvc
class GlobalCorsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void allowsFrontendOriginPreflight() throws Exception {
        mockMvc.perform(options("/auth/login")
                        .header("Origin", "https://app.example.com")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://app.example.com"))
                .andExpect(header().string("Access-Control-Allow-Methods", containsString("POST")));
    }

    @Test
    void allowsBackendOriginPreflight() throws Exception {
        mockMvc.perform(options("/auth/login")
                        .header("Origin", "https://api.example.com")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://api.example.com"));
    }

    @Test
    void allowsExtraOriginPreflight() throws Exception {
        // Extra origins behave exactly like the frontend origin.
        mockMvc.perform(options("/auth/login")
                        .header("Origin", "https://third.example.com")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://third.example.com"));
    }

    @Test
    void rejectsUnknownOriginPreflight() throws Exception {
        mockMvc.perform(options("/auth/login")
                        .header("Origin", "https://evil.example.com")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void clientRequestsDeferToPerClientCors() throws Exception {
        // X-Client-Id requests are owned by ClientCorsFilter; the global source
        // must not add headers even for a globally-allowed origin.
        mockMvc.perform(options("/auth/login")
                        .header("X-Client-Id", "1")
                        .header("Origin", "https://app.example.com")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}