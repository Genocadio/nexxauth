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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AccountLockoutIntegrationTest {

    private static final String[] SLUGS = {"lockout1", "lockout2", "lockout3"};

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void accountLockedAfterMaxFailures() throws Exception {
        String platform = "/" + SLUGS[0];
        String orgAuth = platform + "/auth";
        String boss = registerPlatform("lockout-boss@nexx.io", SLUGS[0]);
        long orgId = createOrganisation(boss, platform, "lo-org1");
        String org = platform + "/organisations/" + orgId;
        String clientKey = createClient(boss, org, "Test Client");

        registerOrgUser(orgAuth, clientKey, "lockable", "correctpass1");

        for (int i = 0; i < 9; i++) {
            mockMvc.perform(post(orgAuth + "/login")
                            .header("X-Client-Id", clientKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("identifier", "lockable", "password", "wrong" + i))))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post(orgAuth + "/login")
                        .header("X-Client-Id", clientKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("identifier", "lockable", "password", "wrong9"))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post(orgAuth + "/login")
                        .header("X-Client-Id", clientKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("identifier", "lockable", "password", "correctpass1"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void successfulLoginClearsFailureCounter() throws Exception {
        String platform = "/" + SLUGS[1];
        String orgAuth = platform + "/auth";
        String boss = registerPlatform("lockout-clear@nexx.io", SLUGS[1]);
        long orgId = createOrganisation(boss, platform, "lo-org2");
        String org = platform + "/organisations/" + orgId;
        String clientKey = createClient(boss, org, "Test Client");

        registerOrgUser(orgAuth, clientKey, "recoverable", "goodpass12");

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post(orgAuth + "/login")
                            .header("X-Client-Id", clientKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("identifier", "recoverable", "password", "bad" + i))))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post(orgAuth + "/login")
                        .header("X-Client-Id", clientKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("identifier", "recoverable", "password", "goodpass12"))))
                .andExpect(status().isOk());

        for (int i = 0; i < 9; i++) {
            mockMvc.perform(post(orgAuth + "/login")
                            .header("X-Client-Id", clientKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("identifier", "recoverable", "password", "bad2_" + i))))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post(orgAuth + "/login")
                        .header("X-Client-Id", clientKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("identifier", "recoverable", "password", "bad2_9"))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post(orgAuth + "/login")
                        .header("X-Client-Id", clientKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("identifier", "recoverable", "password", "goodpass12"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void lockoutIsPerAccountDifferentIdentifiersAreIndependent() throws Exception {
        String platform = "/" + SLUGS[2];
        String orgAuth = platform + "/auth";
        String boss = registerPlatform("lockout-peracct@nexx.io", SLUGS[2]);
        long orgId = createOrganisation(boss, platform, "lo-org3");
        String org = platform + "/organisations/" + orgId;
        String clientKey = createClient(boss, org, "Test Client");

        registerOrgUser(orgAuth, clientKey, "user_a", "pass_a1234");
        registerOrgUser(orgAuth, clientKey, "user_b", "pass_b1234");

        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post(orgAuth + "/login")
                            .header("X-Client-Id", clientKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("identifier", "user_a", "password", "wrong" + i))))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post(orgAuth + "/login")
                        .header("X-Client-Id", clientKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("identifier", "user_a", "password", "pass_a1234"))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post(orgAuth + "/login")
                        .header("X-Client-Id", clientKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("identifier", "user_b", "password", "pass_b1234"))))
                .andExpect(status().isOk());
    }

    // --- helpers ---

    private String registerPlatform(String email, String slug) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "firstName", "F", "lastName", "L",
                                "email", email, "password", "password1",
                                "platformName", "Lockout Platform", "platformSlug", slug))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private long createOrganisation(String boss, String platform, String slug) throws Exception {
        MvcResult result = mockMvc.perform(post(platform + "/organisations")
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Lockout Org", "slug", slug))))
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

    private long registerOrgUser(String orgAuth, String clientKey, String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post(orgAuth + "/register")
                        .header("X-Client-Id", clientKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", username, "password", password,
                                "firstName", "F", "lastName", "L"))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("user").get("id").asLong();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
