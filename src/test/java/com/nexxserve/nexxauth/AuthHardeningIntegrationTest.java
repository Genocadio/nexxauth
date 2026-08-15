package com.nexxserve.nexxauth;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthHardeningIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void disabledUserAccessTokenIsRejectedImmediately() throws Exception {
        String superAccess = tokenOf(register("boss@hardening.com", "Boss Co", "password1"));

        MvcResult add = mockMvc.perform(post("/api/v1/platforms/boss-co/users")
                        .header("Authorization", bearer(superAccess))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "firstName", "Worker",
                                "lastName", "One",
                                "email", "worker@hardening.com",
                                "password", "password1"))))
                .andExpect(status().isCreated())
                .andReturn();
        long workerId = objectMapper.readTree(add.getResponse().getContentAsString()).get("id").asLong();

        String workerAccess = login("worker@hardening.com", "password1");

        // Token works while the account is enabled...
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(workerAccess)))
                .andExpect(status().isOk());

        // ...and is rejected immediately once the account is disabled.
        mockMvc.perform(patch("/api/v1/users/" + workerId)
                        .header("Authorization", bearer(superAccess))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("enabled", false))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(workerAccess)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void passwordChangeRevokesRefreshTokens() throws Exception {
        MvcResult register = register("pw@hardening.com", "Pw Co", "password1");
        String access = tokenOf(register);
        String refresh = refreshOf(register);

        mockMvc.perform(post("/api/v1/auth/me/password")
                        .header("Authorization", bearer(access))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "currentPassword", "password1",
                                "newPassword", "new-password1"))))
                .andExpect(status().isNoContent());

        // The old refresh token must not work anymore.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", refresh))))
                .andExpect(status().isUnauthorized());

        // And the new password must actually be in effect (regression: a bulk
        // revoke used to clear the persistence context and lose the update).
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "pw@hardening.com", "password", "new-password1"))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "pw@hardening.com", "password", "password1"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void emailLoginIsCaseInsensitive() throws Exception {
        MvcResult register = register("MixedCase@Test.com", "Case Co", "password1");
        String email = objectMapper.readTree(register.getResponse().getContentAsString())
                .path("user").path("email").asText();
        assert email.equals("mixedcase@test.com") : "email was not normalized: " + email;

        // Different casing must still authenticate against the same account.
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "MIXEDCASE@test.COM", "password", "password1"))))
                .andExpect(status().isOk());
    }

    @Test
    void refreshTokenReuseRevokesWholeFamily() throws Exception {
        MvcResult register = register("reuse@hardening.com", "Reuse Co", "password1");
        String refresh1 = refreshOf(register);

        MvcResult refresh = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", refresh1))))
                .andExpect(status().isOk())
                .andReturn();
        String refresh2 = objectMapper.readTree(refresh.getResponse().getContentAsString())
                .get("refreshToken").asText();

        // Replaying the already-rotated token is treated as theft...
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", refresh1))))
                .andExpect(status().isUnauthorized());

        // ...and the whole token family is revoked, including the fresh one.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", refresh2))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void blankOptionalFieldsAreRejected() throws Exception {
        String access = tokenOf(register("blank@hardening.com", "Blank Co", "password1"));

        mockMvc.perform(patch("/api/v1/auth/me")
                        .header("Authorization", bearer(access))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("firstName"));
    }

    // --- helpers ---

    private MvcResult register(String email, String platformName, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "firstName", "F",
                                "lastName", "L",
                                "email", email,
                                "password", password,
                                "platformName", platformName))))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private String login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private String tokenOf(MvcResult registerResult) throws Exception {
        return objectMapper.readTree(registerResult.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private String refreshOf(MvcResult registerResult) throws Exception {
        return objectMapper.readTree(registerResult.getResponse().getContentAsString()).get("refreshToken").asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
