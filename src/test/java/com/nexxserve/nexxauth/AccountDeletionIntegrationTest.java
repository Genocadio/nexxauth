package com.nexxserve.nexxauth;

import com.nexxserve.nexxauth.repository.OrganisationUserRepository;
import com.nexxserve.nexxauth.service.OrganisationUserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AccountDeletionIntegrationTest {

    private static final String PLATFORM_SLUG = "deltestplatform";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrganisationUserRepository userRepository;

    @Autowired
    private OrganisationUserService userService;

    @Test
    void testSelfServiceDeleteAndBlockSubsequentLogin() throws Exception {
        String platform = "/" + PLATFORM_SLUG;
        String boss = registerPlatform("del-boss@nexx.io", PLATFORM_SLUG);
        long orgId = createOrganisation(boss, platform);
        String org = platform + "/organisations/" + orgId;
        String clientKey = createClient(boss, org, "Delete Test Client");
        String orgAuth = platform + "/auth";

        // Register user
        String email = "del-user1@nexx.io";
        String username = "deluser1";
        String password = "StrongPassword123!";
        mockMvc.perform(post(orgAuth + "/register")
                        .header("X-Client-Id", clientKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "username", username,
                                "firstName", "Delete",
                                "lastName", "Target",
                                "email", email,
                                "password", password))))
                .andExpect(status().isCreated());

        // Login to get token
        String token = login(orgAuth, clientKey, username, password);
        assertNotNull(token);

        // Self-delete via DELETE /me
        mockMvc.perform(delete(org + "/users/me")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());

        // Verify soft-deleted in DB: deleted_at is set, enabled is false
        var userInDb = userRepository.findByOrganisationIdAndEmail(orgId, email).orElseThrow();
        assertTrue(userInDb.isDeleted());
        assertFalse(userInDb.isEnabled());
        assertNotNull(userInDb.getDeletedAt());

        // Attempting to login with deleted account returns 403 Forbidden
        mockMvc.perform(post(orgAuth + "/login")
                        .header("X-Client-Id", clientKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("identifier", username, "password", password))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("This account has been deleted"));
    }

    @Test
    void testDirectOneStepDeleteEndpointViaCredentials() throws Exception {
        String platform = "/" + PLATFORM_SLUG + "2";
        String boss = registerPlatform("del-boss2@nexx.io", PLATFORM_SLUG + "2");
        long orgId = createOrganisation(boss, platform);
        String org = platform + "/organisations/" + orgId;
        String clientKey = createClient(boss, org, "Delete Direct Client");
        String orgAuth = platform + "/auth";

        String email = "del-direct@nexx.io";
        String username = "deldirect";
        String password = "DirectPassword123!";
        mockMvc.perform(post(orgAuth + "/register")
                        .header("X-Client-Id", clientKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "username", username,
                                "firstName", "Direct",
                                "lastName", "Delete",
                                "email", email,
                                "password", password))))
                .andExpect(status().isCreated());

        // Direct delete in 1 step via POST /auth/delete-account
        mockMvc.perform(post(orgAuth + "/delete-account")
                        .header("X-Client-Id", clientKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("identifier", username, "password", password))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Subsequent login blocked
        mockMvc.perform(post(orgAuth + "/login")
                        .header("X-Client-Id", clientKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("identifier", username, "password", password))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("This account has been deleted"));

        // 30-day purge test
        var userInDb = userRepository.findByOrganisationIdAndEmail(orgId, email).orElseThrow();
        userInDb.setDeletedAt(Instant.now().minus(31, ChronoUnit.DAYS));
        userRepository.save(userInDb);

        int purged = userService.purgeExpiredSoftDeletedUsers(Instant.now().minus(30, ChronoUnit.DAYS));
        assertTrue(purged >= 1);
        assertTrue(userRepository.findByOrganisationIdAndEmail(orgId, email).isEmpty());
    }

    private String registerPlatform(String email, String slug) throws Exception {
        MvcResult res = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "firstName", "Boss", "lastName", "User",
                                "email", email, "password", "master-password",
                                "platformName", "Platform " + slug, "platformSlug", slug))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private long createOrganisation(String bossToken, String platform) throws Exception {
        MvcResult res = mockMvc.perform(post(platform + "/organisations")
                        .header("Authorization", bearer(bossToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Test Org", "slug", "test-org-" + System.nanoTime()))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();
    }

    private String createClient(String bossToken, String org, String name) throws Exception {
        MvcResult res = mockMvc.perform(post(org + "/clients")
                        .header("Authorization", bearer(bossToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", name, "type", "WEB"))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("clientKey").asText();
    }

    private String login(String orgAuth, String clientKey, String identifier, String password) throws Exception {
        MvcResult result = mockMvc.perform(post(orgAuth + "/login")
                        .header("X-Client-Id", clientKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("identifier", identifier, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
