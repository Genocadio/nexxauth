package com.nexxserve.nexxauth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void fullAuthLifecycle() throws Exception {
        // --- register creates a platform + super user, returns tokens ---
        MvcResult register = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "firstName", "Ada",
                                "lastName", "Lovelace",
                                "email", "ada@example.com",
                                "password", "sup3r-secret",
                                "phone", "+123456789",
                                "platformName", "Analytical Engines"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.email").value("ada@example.com"))
                .andExpect(jsonPath("$.user.role").value("SUPER_USER"))
                .andExpect(jsonPath("$.user.platform.slug").value("analytical-engines"))
                .andReturn();

        JsonNode registerBody = objectMapper.readTree(register.getResponse().getContentAsString());
        String accessToken = registerBody.get("accessToken").asText();
        String refreshToken = registerBody.get("refreshToken").asText();

        // --- me with access token ---
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Ada"))
                .andExpect(jsonPath("$.role").value("SUPER_USER"));

        // --- login with credentials ---
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "ada@example.com", "password", "sup3r-secret"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();
        JsonNode loginBody = objectMapper.readTree(login.getResponse().getContentAsString());
        String loginAccess = loginBody.get("accessToken").asText();

        // --- platform view ---
        mockMvc.perform(get("/analytical-engines")
                        .header("Authorization", bearer(loginAccess)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("analytical-engines"))
                .andExpect(jsonPath("$.userCount").value(1))
                .andExpect(jsonPath("$.apiBaseUrl").value(org.hamcrest.Matchers.nullValue()));

        // --- self profile update ---
        mockMvc.perform(patch("/api/v1/auth/me")
                        .header("Authorization", bearer(loginAccess))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("phone", "+000000000"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("+000000000"));

        // --- super user adds a read-only member ---
        MvcResult addUser = mockMvc.perform(post("/analytical-engines/users")
                        .header("Authorization", bearer(loginAccess))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "firstName", "Grace",
                                "lastName", "Hopper",
                                "email", "grace@example.com",
                                "password", "readonly-pw"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("READ_ONLY"))
                .andReturn();
        long graceId = objectMapper.readTree(addUser.getResponse().getContentAsString()).get("id").asLong();

        // --- read-only user logs in and can read but not write ---
        MvcResult graceLogin = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "grace@example.com", "password", "readonly-pw"))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode graceBody = objectMapper.readTree(graceLogin.getResponse().getContentAsString());
        String graceAccess = graceBody.get("accessToken").asText();

        mockMvc.perform(get("/analytical-engines/users")
                        .header("Authorization", bearer(graceAccess)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(post("/analytical-engines/users")
                        .header("Authorization", bearer(graceAccess))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "firstName", "X",
                                "lastName", "Y",
                                "email", "x@example.com",
                                "password", "password1"))))
                .andExpect(status().isForbidden());

        // --- super user updates the member's role ---
        mockMvc.perform(patch("/api/v1/users/" + graceId)
                        .header("Authorization", bearer(loginAccess))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("role", "SUPER_USER", "firstName", "Gracey"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("SUPER_USER"))
                .andExpect(jsonPath("$.firstName").value("Gracey"));

        // --- refresh rotates the token (old one becomes unusable) ---
        MvcResult refresh = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", refreshToken))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();
        JsonNode refreshBody = objectMapper.readTree(refresh.getResponse().getContentAsString());
        String newRefreshToken = refreshBody.get("refreshToken").asText();
        assertThat(newRefreshToken).isNotEqualTo(refreshToken);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", refreshToken))))
                .andExpect(status().isUnauthorized());

        // --- logout revokes the refresh token ---
        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", newRefreshToken))))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", newRefreshToken))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void duplicateEmailAndBadLoginAreRejected() throws Exception {
        String body = json(Map.of(
                "firstName", "A",
                "lastName", "B",
                "email", "dup@example.com",
                "password", "password1",
                "platformName", "Dup Corp"));

        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "dup@example.com", "password", "wrong-password"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidAccessTokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
