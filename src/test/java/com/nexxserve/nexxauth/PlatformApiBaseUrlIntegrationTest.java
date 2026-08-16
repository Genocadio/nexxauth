package com.nexxserve.nexxauth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The platform's copiable API base URL ({@code apiBaseUrl}) combines the
 * backend's configured public origin ({@code app.cors.backend-origin} /
 * BACKEND_PUBLIC_URL) with the platform slug — the value the console shows as
 * the dashboard's API URL.
 */
@SpringBootTest(properties = "app.cors.backend-origin=https://api.example.com")
@AutoConfigureMockMvc
class PlatformApiBaseUrlIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void platformApiBaseUrlCombinesPublicOriginWithSlug() throws Exception {
        MvcResult register = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "firstName", "Anna",
                                "lastName", "Api",
                                "email", "anna-api@example.com",
                                "password", "password1",
                                "platformName", "API Base Platform",
                                "platformSlug", "api-base-platform"))))
                .andExpect(status().isCreated())
                .andReturn();
        String accessToken = objectMapper.readTree(register.getResponse().getContentAsString())
                .get("accessToken").asText();

        mockMvc.perform(get("/api-base-platform")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiBaseUrl").value("https://api.example.com/api-base-platform"));
    }

    private String json(Map<String, String> body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }
}