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

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Organisation-level auth config: per-org password rules (length, expiry,
 * history), per-user auth types (users without a password cannot log in), and
 * the guarantee that all of this is org-scoped and never touches platform auth.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OrganisationAuthConfigIntegrationTest {

    private static final String[] SLUGS = {"ac1", "ac2", "ac3", "ac4", "ac5", "ac6"};

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private com.nexxserve.nexxauth.repository.OrganisationAuthConfigRepository authConfigRepository;

    @Test
    void defaultsApplyAndConfigIsReadable() throws Exception {
        String platform = "/" + SLUGS[0];
        String boss = registerPlatform("ac-boss@nexx.io", SLUGS[0]);
        long orgId = createOrganisation(boss, platform, "Default Org", "default-org");
        String configPath = platform + "/organisations/" + orgId + "/auth-config";

        // defaults: PASSWORD auth, 8-72 length, no expiry, no history
        mockMvc.perform(get(configPath).header("Authorization", bearer(boss)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authType").value("PASSWORD"))
                .andExpect(jsonPath("$.passwordMinLength").value(8))
                .andExpect(jsonPath("$.passwordMaxLength").value(72))
                .andExpect(jsonPath("$.passwordExpirationDays").value(0))
                .andExpect(jsonPath("$.passwordHistoryCount").value(0));

        // the lazy default must actually be persisted (a read-only GET that
        // inserts fails on Postgres - regression guard for that path)
        org.junit.jupiter.api.Assertions.assertTrue(
                authConfigRepository.findByOrganisationId(orgId).isPresent(),
                "GET auth-config should persist the default config row");
    }

    @Test
    void minLengthRuleIsEnforcedOnRegister() throws Exception {
        String platform = "/" + SLUGS[1];
        String boss = registerPlatform("ac-min-boss@nexx.io", SLUGS[1]);
        long orgId = createOrganisation(boss, platform, "Min Org", "min-org");
        String orgAuth = platform + "/auth";
        String clientKey = createClient(boss, platform + "/organisations/" + orgId, "Test Client");

        // tighten the minimum length to 10
        mockMvc.perform(patch(platform + "/organisations/" + orgId + "/auth-config")
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("passwordMinLength", 10))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passwordMinLength").value(10));

        // an 8-char password now violates the org rule
        mockMvc.perform(post(orgAuth + "/register")
                        .header("X-Client-Id", clientKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "organisationId", orgId, "username", "shorty",
                                "password", "shortpass", "firstName", "S", "lastName", "Y"))))
                .andExpect(status().isBadRequest());

        // a 12-char password passes
        MvcResult reg = mockMvc.perform(post(orgAuth + "/register")
                        .header("X-Client-Id", clientKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "organisationId", orgId, "username", "longer",
                                "password", "alongerpass", "firstName", "L", "lastName", "G"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.authTypes[0]").value("PASSWORD"))
                .andReturn();
        String token = objectMapper.readTree(reg.getResponse().getContentAsString()).get("accessToken").asText();
        mockMvc.perform(get(platform + "/organisations/" + orgId + "/users/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
    }

    @Test
    void userWithoutPasswordCannotLoginUntilOneIsSet() throws Exception {
        String platform = "/" + SLUGS[2];
        String boss = registerPlatform("ac-noauth-boss@nexx.io", SLUGS[2]);
        long orgId = createOrganisation(boss, platform, "NoAuth Org", "noauth-org");
        String org = platform + "/organisations/" + orgId;
        String orgAuth = platform + "/auth";
        String clientKey = createClient(boss, org, "Test Client");

        // create a user WITHOUT a password: no auth configured, authTypes empty
        MvcResult created = mockMvc.perform(post(org + "/users")
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("firstName", "N", "lastName", "O", "username", "nobody"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.authTypes").isEmpty())
                .andReturn();
        long userId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        // cannot log in yet
        mockMvc.perform(post(orgAuth + "/login")
                        .header("X-Client-Id", clientKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("organisationId", orgId, "identifier", "nobody", "password", "whatever"))))
                .andExpect(status().isUnauthorized());

        // set a password via PATCH: now the user can log in
        mockMvc.perform(patch(org + "/users/" + userId)
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("password", "newpass123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authTypes[0]").value("PASSWORD"));
        mockMvc.perform(post(orgAuth + "/login")
                        .header("X-Client-Id", clientKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("organisationId", orgId, "identifier", "nobody", "password", "newpass123"))))
                .andExpect(status().isOk());

        // clearing auth (empty password) disables login again
        mockMvc.perform(patch(org + "/users/" + userId)
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("password", ""))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authTypes").isEmpty());
        mockMvc.perform(post(orgAuth + "/login")
                        .header("X-Client-Id", clientKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("organisationId", orgId, "identifier", "nobody", "password", "newpass123"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void historyPreventsPasswordReuse() throws Exception {
        String platform = "/" + SLUGS[3];
        String boss = registerPlatform("ac-hist-boss@nexx.io", SLUGS[3]);
        long orgId = createOrganisation(boss, platform, "Hist Org", "hist-org");
        String org = platform + "/organisations/" + orgId;
        String orgAuth = platform + "/auth";
        String clientKey = createClient(boss, org, "Test Client");

        // enable history (keep last 2)
        mockMvc.perform(patch(platform + "/organisations/" + orgId + "/auth-config")
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("passwordHistoryCount", 2))))
                .andExpect(status().isOk());

        MvcResult reg = mockMvc.perform(post(orgAuth + "/register")
                        .header("X-Client-Id", clientKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "organisationId", orgId, "username", "cycler",
                                "password", "firstpass", "firstName", "C", "lastName", "R"))))
                .andExpect(status().isCreated())
                .andReturn();
        long userId = objectMapper.readTree(reg.getResponse().getContentAsString()).get("user").get("id").asLong();

        // change to a second password (allowed)
        mockMvc.perform(patch(org + "/users/" + userId)
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("password", "secondpass"))))
                .andExpect(status().isOk());

        // reuse of the ORIGINAL password is now rejected (it is in history)
        mockMvc.perform(patch(org + "/users/" + userId)
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("password", "firstpass"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void expiredPasswordBlocksLogin() throws Exception {
        String platform = "/" + SLUGS[4];
        String boss = registerPlatform("ac-exp-boss@nexx.io", SLUGS[4]);
        long orgId = createOrganisation(boss, platform, "Exp Org", "exp-org");
        String orgAuth = platform + "/auth";
        String clientKey = createClient(boss, platform + "/organisations/" + orgId, "Test Client");

        mockMvc.perform(post(orgAuth + "/register")
                        .header("X-Client-Id", clientKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
"organisationId", orgId, "username", "aged",
                                "password", "oldpass12", "firstName", "A", "lastName", "G"))))
                .andExpect(status().isCreated());

        // passwords expire after 1 day; the just-created one is still fresh
        mockMvc.perform(patch(platform + "/organisations/" + orgId + "/auth-config")
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("passwordExpirationDays", 1))))
                .andExpect(status().isOk());
        mockMvc.perform(post(orgAuth + "/login")
                        .header("X-Client-Id", clientKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("organisationId", orgId, "identifier", "aged", "password", "oldpass12"))))
                .andExpect(status().isOk());
    }

    @Test
    void configIsOrgScopedAndPlatformAuthIsUntouched() throws Exception {
        String platform = "/" + SLUGS[5];
        String boss = registerPlatform("ac-scope-boss@nexx.io", SLUGS[5]);
        long scopeOrgId = createOrganisation(boss, platform, "Scope Org", "scope-org");
        long otherOrgId = createOrganisation(boss, platform, "Other Org", "other-org");

        // tighten rules only for scope-org
        mockMvc.perform(patch(platform + "/organisations/" + scopeOrgId + "/auth-config")
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("passwordMinLength", 12))))
                .andExpect(status().isOk());
        mockMvc.perform(get(platform + "/organisations/" + otherOrgId + "/auth-config").header("Authorization", bearer(boss)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passwordMinLength").value(8));

        // platform register/login are unaffected by org rules: a 9-char
        // password passes the platform's own 8-72 rule even though scope-org
        // demands 12 (platform rules never read the org config)
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "firstName", "P", "lastName", "T",
                                "email", "ac-platform@nexx.io", "password", "ninechars",
                                "platformName", "PT Co", "platformSlug", "pt-co"))))
                .andExpect(status().isCreated());
    }

    // --- helpers ---

    private String createClient(String boss, String org, String name) throws Exception {
        MvcResult result = mockMvc.perform(post(org + "/clients")
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", name, "type", "WEB"))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("clientKey").asText();
    }

    private String registerPlatform(String email, String slug) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "firstName", "F", "lastName", "L",
                                "email", email, "password", "password1",
                                "platformName", "Auth Config Platform", "platformSlug", slug))))
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

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
