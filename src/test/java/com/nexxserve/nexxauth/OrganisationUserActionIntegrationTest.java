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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Organisation user actions: a temporary password (set by a platform user, or
 * forced later) gates the session with a fixed 5-minute access token, no
 * refresh token and only the change-password endpoint reachable, until the user
 * changes their password. Required organisation user fields surface the
 * non-gating UPDATE_PROFILE action, which does not restrict tokens.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OrganisationUserActionIntegrationTest {

    private static final String[] SLUGS = {"action1", "action2", "action3", "action4"};
    private static final String ORG_SLUG = "action-org";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void temporaryPasswordGatesSessionUntilChanged() throws Exception {
        String platform = "/" + SLUGS[0];
        String orgAuth = platform + "/auth";
        String boss = registerPlatform("action-boss@nexx.io", SLUGS[0]);
        long orgId = createOrganisation(boss, platform);
        String org = platform + "/organisations/" + orgId;

        // platform user registers an org user with a temporary password
        mockMvc.perform(post(org + "/users")
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "firstName", "T", "lastName", "Emp",
                                "username", "tempuser",
                                "password", "temp-pass1",
                                "temporaryPassword", true))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.temporaryPassword").value(true));

        String clientKey = createClient(boss, org, "Test Client");

        // first login: CHANGE_PASSWORD action, 5-min access, NO refresh token
        MvcResult login = mockMvc.perform(post(orgAuth + "/login")
                        .header("X-Client-Id", clientKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("organisationId", orgId, "identifier", "tempuser", "password", "temp-pass1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actions.length()").value(1))
                .andExpect(jsonPath("$.actions[0]").value("CHANGE_PASSWORD"))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.expiresInSeconds").value(300))
                .andReturn();
        String gatedToken = objectMapper.readTree(login.getResponse().getContentAsString())
                .get("accessToken").asText();

        // while the action is pending, all other org endpoints are closed
        mockMvc.perform(get(org + "/users/me").header("Authorization", bearer(gatedToken)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(org + "/users").header("Authorization", bearer(gatedToken)))
                .andExpect(status().isUnauthorized());

        // the change-password endpoint is reachable and completes the action
        mockMvc.perform(post(org + "/users/me/change-password")
                        .header("Authorization", bearer(gatedToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("currentPassword", "temp-pass1", "newPassword", "new-pass-123"))))
                .andExpect(status().isNoContent());

        // after the change the session is unrestricted again
        mockMvc.perform(post(orgAuth + "/login")
                        .header("X-Client-Id", clientKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("organisationId", orgId, "identifier", "tempuser", "password", "new-pass-123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actions.length()").value(0))
                .andExpect(jsonPath("$.refreshToken").exists())
                .andExpect(jsonPath("$.expiresInSeconds").value(900));
    }

    @Test
    void wrongCurrentPasswordCannotCompleteTheAction() throws Exception {
        String platform = "/" + SLUGS[3];
        String orgAuth = platform + "/auth";
        String boss = registerPlatform("action-wrong@nexx.io", SLUGS[3]);
        long orgId = createOrganisation(boss, platform);
        String org = platform + "/organisations/" + orgId;

        mockMvc.perform(post(org + "/users")
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "firstName", "T", "lastName", "Emp",
                                "username", "tempuser2",
                                "password", "temp-pass1",
                                "temporaryPassword", true))))
                .andExpect(status().isCreated());

        String clientKey = createClient(boss, org, "Test Client");
        MvcResult login = mockMvc.perform(post(orgAuth + "/login")
                        .header("X-Client-Id", clientKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("organisationId", orgId, "identifier", "tempuser2", "password", "temp-pass1"))))
                .andExpect(status().isOk())
                .andReturn();
        String gatedToken = objectMapper.readTree(login.getResponse().getContentAsString())
                .get("accessToken").asText();

        mockMvc.perform(post(org + "/users/me/change-password")
                        .header("Authorization", bearer(gatedToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("currentPassword", "wrong", "newPassword", "new-pass-123"))))
                .andExpect(status().isUnauthorized());

        // the action is still pending: login still gates the session
        mockMvc.perform(post(orgAuth + "/login")
                        .header("X-Client-Id", clientKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("organisationId", orgId, "identifier", "tempuser2", "password", "temp-pass1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actions[0]").value("CHANGE_PASSWORD"))
                .andExpect(jsonPath("$.refreshToken").doesNotExist());
    }

    @Test
    void adminTriggeredPasswordChangeKillsSessionsAndGates() throws Exception {
        String platform = "/" + SLUGS[1];
        String orgAuth = platform + "/auth";
        String boss = registerPlatform("action-force@nexx.io", SLUGS[1]);
        long orgId = createOrganisation(boss, platform);
        String org = platform + "/organisations/" + orgId;

        String clientKey = createClient(boss, org, "Test Client");
        // register a user normally (no temporary password), then log in
        long userId = registerOrgUser(orgAuth, clientKey, "forced", "org-pass-1");
        MvcResult login = mockMvc.perform(post(orgAuth + "/login")
                        .header("X-Client-Id", clientKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("organisationId", orgId, "identifier", "forced", "password", "org-pass-1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actions.length()").value(0))
                .andReturn();
        String oldRefresh = objectMapper.readTree(login.getResponse().getContentAsString())
                .get("refreshToken").asText();

        // admin triggers a forced password change
        mockMvc.perform(patch(org + "/users/" + userId)
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("temporaryPassword", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.temporaryPassword").value(true));

        // the old session is dead: refresh is rejected
        mockMvc.perform(post(orgAuth + "/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", oldRefresh))))
                .andExpect(status().isUnauthorized());

        // login now gates the session until the user changes the password
        mockMvc.perform(post(orgAuth + "/login")
                        .header("X-Client-Id", clientKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("organisationId", orgId, "identifier", "forced", "password", "org-pass-1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actions[0]").value("CHANGE_PASSWORD"))
                .andExpect(jsonPath("$.refreshToken").doesNotExist());
    }

    @Test
    void requiredFieldSurfacesNonGatingUpdateProfileAction() throws Exception {
        String platform = "/" + SLUGS[2];
        String orgAuth = platform + "/auth";
        String boss = registerPlatform("action-profile@nexx.io", SLUGS[2]);
        long orgId = createOrganisation(boss, platform);
        String org = platform + "/organisations/" + orgId;

        // a required org user field
        mockMvc.perform(post(org + "/user-fields")
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "key", "department",
                                "fieldType", "STRING", "loginEnabled", false,
                                "required", true))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.required").value(true));

        // platform user registers an org user without the required value
        mockMvc.perform(post(org + "/users")
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "firstName", "U", "lastName", "Emp",
                                "username", "profuser",
                                "password", "org-pass-1"))))
                .andExpect(status().isCreated());

        String clientKey = createClient(boss, org, "Test Client");

        // login: UPDATE_PROFILE is advisory - refresh token and normal access
        // token are still issued, and other endpoints keep working
        MvcResult login = mockMvc.perform(post(orgAuth + "/login")
                        .header("X-Client-Id", clientKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("organisationId", orgId, "identifier", "profuser", "password", "org-pass-1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actions.length()").value(1))
                .andExpect(jsonPath("$.actions[0]").value("UPDATE_PROFILE"))
                .andExpect(jsonPath("$.refreshToken").exists())
                .andExpect(jsonPath("$.expiresInSeconds").value(900))
                .andReturn();
        String token = objectMapper.readTree(login.getResponse().getContentAsString())
                .get("accessToken").asText();

        // non-gating: the profile and user endpoints are not blocked
        mockMvc.perform(get(org + "/users/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk());

        // the user completes the action by updating their own profile
        mockMvc.perform(patch(org + "/users/me")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("metadata", Map.of("department", "engineering")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metadata.department").value("engineering"));

        // next login has no pending actions
        mockMvc.perform(post(orgAuth + "/login")
                        .header("X-Client-Id", clientKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("organisationId", orgId, "identifier", "profuser", "password", "org-pass-1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actions.length()").value(0));
    }

    // --- helpers ---

    private String registerPlatform(String email, String slug) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "firstName", "F", "lastName", "L",
                                "email", email, "password", "password1",
                                "platformName", "Action Platform", "platformSlug", slug))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private long createOrganisation(String boss, String platform) throws Exception {
        MvcResult result = mockMvc.perform(post(platform + "/organisations")
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Action Org", "slug", ORG_SLUG))))
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

    private long registerOrgUser(String orgAuth, String clientKey, String identifier, String password) throws Exception {
        MvcResult result = mockMvc.perform(post(orgAuth + "/register")
                        .header("X-Client-Id", clientKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "username", identifier,
                                "password", password,
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
