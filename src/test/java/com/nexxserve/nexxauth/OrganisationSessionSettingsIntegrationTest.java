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

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Organisation-level session settings: default values, per-org access/refresh
 * token lifetimes (actually applied to issued tokens), the concurrent-session
 * limit (oldest session evicted on overflow), access control and validation.
 * All org-scoped; the platform auth flow is untouched.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OrganisationSessionSettingsIntegrationTest {

    private static final String[] SLUGS = {"ss1", "ss2", "ss3", "ss4", "ss5", "ss6", "ss7", "ss8"};

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private com.nexxserve.nexxauth.repository.OrganisationSessionSettingsRepository settingsRepository;

    @Autowired
    private com.nexxserve.nexxauth.repository.OrganisationRefreshTokenRepository refreshTokenRepository;

    @Test
    void defaultsApplyAndSettingsAreReadable() throws Exception {
        String platform = "/" + SLUGS[0];
        String boss = registerPlatform("ss-boss@nexx.io", SLUGS[0]);
        long orgId = createOrganisation(boss, platform, "Default Org", "default-org");
        String settingsPath = platform + "/organisations/" + orgId + "/session-settings";

        // defaults match app.jwt.*: 15m access, 7d refresh, 5 sessions
        mockMvc.perform(get(settingsPath).header("Authorization", bearer(boss)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessTokenTtlSeconds").value(900))
                .andExpect(jsonPath("$.refreshTokenTtlSeconds").value(604800))
                .andExpect(jsonPath("$.maxSessionsPerUser").value(5));

        // the lazy default must actually be persisted (read-only GET that
        // inserts fails on Postgres - same regression guard as auth-config)
        org.junit.jupiter.api.Assertions.assertTrue(
                settingsRepository.findByOrganisationId(orgId).isPresent(),
                "GET session-settings should persist the default settings row");
    }

    @Test
    void accessTtlIsAppliedToIssuedTokens() throws Exception {
        String platform = "/" + SLUGS[1];
        String boss = registerPlatform("ss-ttl-boss@nexx.io", SLUGS[1]);
        long orgId = createOrganisation(boss, platform, "Ttl Org", "ttl-org");
        String settingsPath = platform + "/organisations/" + orgId + "/session-settings";

        // shorten the access token to 2 minutes
        mockMvc.perform(patch(settingsPath)
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("accessTokenTtlSeconds", 120))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessTokenTtlSeconds").value(120));

        OrgTokens tokens = registerOrgUser(boss, platform, orgId, "ttluser", "pass1234");
        // the login response reports the org-specific lifetime...
        org.junit.jupiter.api.Assertions.assertEquals(120, tokens.expiresInSeconds());
        // ...and the token itself is actually signed with it
        JsonNode claims = decodeClaims(tokens.accessToken());
        long lifetime = claims.get("exp").asLong() - claims.get("iat").asLong();
        org.junit.jupiter.api.Assertions.assertEquals(120, lifetime);
    }

    @Test
    void refreshTtlIsAppliedToIssuedTokens() throws Exception {
        String platform = "/" + SLUGS[2];
        String boss = registerPlatform("ss-refttl-boss@nexx.io", SLUGS[2]);
        long refttlOrgId = createOrganisation(boss, platform, "RefTtl Org", "refttl-org");

        // refresh tokens live 1 hour instead of 7 days
        mockMvc.perform(patch(platform + "/organisations/" + refttlOrgId + "/session-settings")
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshTokenTtlSeconds", 3600))))
                .andExpect(status().isOk());

        OrgTokens tokens = registerOrgUser(boss, platform, refttlOrgId, "refttluser", "pass1234");
        List<com.nexxserve.nexxauth.entity.OrganisationRefreshToken> active =
                refreshTokenRepository.findActiveByUserIdOrderByExpiresAtAsc(tokens.userId(), Instant.now());
        org.junit.jupiter.api.Assertions.assertEquals(1, active.size());
        Duration remaining = Duration.between(Instant.now(), active.get(0).getExpiresAt());
        org.junit.jupiter.api.Assertions.assertTrue(
                Math.abs(remaining.toSeconds() - 3600) < 60,
                "refresh token should expire ~1h after issue, was " + remaining);
    }

    @Test
    void maxSessionsEvictsOldestSession() throws Exception {
        String platform = "/" + SLUGS[3];
        String boss = registerPlatform("ss-max-boss@nexx.io", SLUGS[3]);
        long orgId = createOrganisation(boss, platform, "Max Org", "max-org");
        String orgAuth = platform + "/auth";

        // only one concurrent session allowed
        mockMvc.perform(patch(platform + "/organisations/" + orgId + "/session-settings")
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("maxSessionsPerUser", 1))))
                .andExpect(status().isOk());

        OrgTokens first = registerOrgUser(boss, platform, orgId, "maxuser", "pass1234");
        OrgTokens second = loginOrg(orgAuth, orgId, "maxuser", "pass1234");

        // the second login evicted the first session: its refresh token is dead
        mockMvc.perform(post(orgAuth + "/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", first.refreshToken()))))
                .andExpect(status().isUnauthorized());

        // the newest session still refreshes fine
        mockMvc.perform(post(orgAuth + "/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", second.refreshToken()))))
                .andExpect(status().isOk());

        // with room for two sessions, a different user keeps both alive
        mockMvc.perform(patch(platform + "/organisations/" + orgId + "/session-settings")
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("maxSessionsPerUser", 2))))
                .andExpect(status().isOk());
        OrgTokens third = registerOrgUser(boss, platform, orgId, "maxuser2", "pass1234");
        OrgTokens fourth = loginOrg(orgAuth, orgId, "maxuser2", "pass1234");
        mockMvc.perform(post(orgAuth + "/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", third.refreshToken()))))
                .andExpect(status().isOk());
        mockMvc.perform(post(orgAuth + "/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", fourth.refreshToken()))))
                .andExpect(status().isOk());
    }

    @Test
    void readOnlyMemberAndOrgUserCannotWrite() throws Exception {
        String platform = "/" + SLUGS[4];
        String boss = registerPlatform("ss-acl-boss@nexx.io", SLUGS[4]);
        long orgId = createOrganisation(boss, platform, "Acl Org", "acl-org");
        String settingsPath = platform + "/organisations/" + orgId + "/session-settings";
        String orgAuth = platform + "/auth";

        // a read-only platform member can read but not change settings
        String readOnly = registerReadOnlyMember(boss, platform);
        mockMvc.perform(get(settingsPath).header("Authorization", bearer(readOnly)))
                .andExpect(status().isOk());
        mockMvc.perform(patch(settingsPath)
                        .header("Authorization", bearer(readOnly))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("maxSessionsPerUser", 3))))
                .andExpect(status().isForbidden());

        // an org user can read the org's settings but not change them
        OrgTokens orgUser = registerOrgUser(boss, platform, orgId, "acluser", "pass1234");
        mockMvc.perform(get(settingsPath).header("Authorization", bearer(orgUser.accessToken())))
                .andExpect(status().isOk());
        mockMvc.perform(patch(settingsPath)
                        .header("Authorization", bearer(orgUser.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("maxSessionsPerUser", 3))))
                .andExpect(status().isForbidden());

        // org auth itself is untouched by all of this
        mockMvc.perform(post(orgAuth + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("organisationId", orgId, "identifier", "acluser", "password", "pass1234"))))
                .andExpect(status().isOk());
    }

    @Test
    void invalidValuesAreRejected() throws Exception {
        String platform = "/" + SLUGS[5];
        String boss = registerPlatform("ss-val-boss@nexx.io", SLUGS[5]);
        long valOrgId = createOrganisation(boss, platform, "Val Org", "val-org");
        String settingsPath = platform + "/organisations/" + valOrgId + "/session-settings";

        // refresh must outlive access
        mockMvc.perform(patch(settingsPath)
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshTokenTtlSeconds", 60))))
                .andExpect(status().isBadRequest());

        // access TTL below the 60s floor
        mockMvc.perform(patch(settingsPath)
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("accessTokenTtlSeconds", 30))))
                .andExpect(status().isBadRequest());

        // sessions must be >= 1
        mockMvc.perform(patch(settingsPath)
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("maxSessionsPerUser", 0))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void clientSessionOverridesTakePrecedenceOverOrgDefaults() throws Exception {
        String boss = registerPlatform("ss-client-override@nexx.io", "ss-co");
        String platform = "/ss-co";
        long orgId = createOrganisation(boss, platform, "Client Override Org", "client-override-org");
        String clientsPath = platform + "/organisations/" + orgId + "/clients";
        String orgAuth = platform + "/auth";

        // org defaults: 900 access, 604800 refresh, 5 sessions
        // set org access TTL to 300s to verify client override takes precedence
        mockMvc.perform(patch(platform + "/organisations/" + orgId + "/session-settings")
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("accessTokenTtlSeconds", 300))))
                .andExpect(status().isOk());

        // create a client with session overrides: 120s access, 7200s refresh
        Client overrideClient = createTokenClientWithOverrides(boss, clientsPath, "Mobile Server", "SERVER",
                120L, 7200L, 3);
        // create a client without overrides (uses org defaults)
        Client defaultClient = createTokenClientWithOverrides(boss, clientsPath, "Web Server", "SERVER",
                null, null, null);

        // register through the override client -> uses client's 120s access TTL
        MvcResult overrideReg = mockMvc.perform(post(orgAuth + "/register")
                        .header("X-Client-Id", overrideClient.clientKey())
                        .header("Authorization", bearer(overrideClient.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("organisationId", orgId, "username", "overrideuser",
                                "password", "pass1234", "firstName", "O", "lastName", "U"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.expiresInSeconds").value(120))
                .andReturn();
        OrgTokens overrideTokens = toTokens(overrideReg.getResponse().getContentAsString());

        // register through the default client -> uses org's 300s access TTL
        MvcResult defaultReg = mockMvc.perform(post(orgAuth + "/register")
                        .header("X-Client-Id", defaultClient.clientKey())
                        .header("Authorization", bearer(defaultClient.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("organisationId", orgId, "username", "defaultuser",
                                "password", "pass1234", "firstName", "D", "lastName", "U"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.expiresInSeconds").value(300))
                .andReturn();

        // verify the override client's refresh token has the client's TTL (7200s)
        List<com.nexxserve.nexxauth.entity.OrganisationRefreshToken> overrideTokens_ =
                refreshTokenRepository.findActiveByUserIdOrderByExpiresAtAsc(
                        overrideTokens.userId(), Instant.now());
        org.junit.jupiter.api.Assertions.assertEquals(1, overrideTokens_.size());
        Duration overrideRemaining = Duration.between(Instant.now(), overrideTokens_.get(0).getExpiresAt());
        org.junit.jupiter.api.Assertions.assertTrue(
                Math.abs(overrideRemaining.toSeconds() - 7200) < 60,
                "override client refresh token should expire ~2h after issue, was " + overrideRemaining);

        // verify the default client's refresh token has the org default TTL (604800s)
        long defaultUserId = objectMapper.readTree(defaultReg.getResponse().getContentAsString())
                .get("user").get("id").asLong();
        List<com.nexxserve.nexxauth.entity.OrganisationRefreshToken> defaultTokens =
                refreshTokenRepository.findActiveByUserIdOrderByExpiresAtAsc(defaultUserId, Instant.now());
        org.junit.jupiter.api.Assertions.assertEquals(1, defaultTokens.size());
        Duration defaultRemaining = Duration.between(Instant.now(), defaultTokens.get(0).getExpiresAt());
        org.junit.jupiter.api.Assertions.assertTrue(
                Math.abs(defaultRemaining.toSeconds() - 604800) < 60,
                "default client refresh token should expire ~7d after issue, was " + defaultRemaining);

        // refresh through the override client should keep the override TTL
        mockMvc.perform(post(orgAuth + "/refresh")
                        .header("X-Client-Id", overrideClient.clientKey())
                        .header("Authorization", bearer(overrideClient.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", overrideTokens.refreshToken()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresInSeconds").value(120));
    }

    @Test
    void clientSessionOverridesAppearInClientResponse() throws Exception {
        String boss = registerPlatform("ss-client-resp@nexx.io", "ss-cr");
        String platform = "/ss-cr";
        long orgId = createOrganisation(boss, platform, "Client Resp Org", "client-resp-org");
        String clientsPath = platform + "/organisations/" + orgId + "/clients";

        // create with overrides
        String key1 = createClientWithOverrides(boss, clientsPath, "Mobile", "ANDROID", 180L, 86400L, 10);
        mockMvc.perform(get(clientsPath + "/" + key1).header("Authorization", bearer(boss)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessTokenTtlSeconds").value(180))
                .andExpect(jsonPath("$.refreshTokenTtlSeconds").value(86400))
                .andExpect(jsonPath("$.maxSessionsPerUser").value(10));

        // create without overrides -> nulls in response
        String key2 = createClientWithOverrides(boss, clientsPath, "Web", "WEB", null, null, null);
        JsonNode webClient = objectMapper.readTree(
                mockMvc.perform(get(clientsPath + "/" + key2).header("Authorization", bearer(boss)))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString());
        org.junit.jupiter.api.Assertions.assertTrue(webClient.get("accessTokenTtlSeconds").isNull(),
                "accessTokenTtlSeconds should be null when not overridden");
        org.junit.jupiter.api.Assertions.assertTrue(webClient.get("refreshTokenTtlSeconds").isNull(),
                "refreshTokenTtlSeconds should be null when not overridden");
        org.junit.jupiter.api.Assertions.assertTrue(webClient.get("maxSessionsPerUser").isNull(),
                "maxSessionsPerUser should be null when not overridden");

        // update: set overrides on the web client
        mockMvc.perform(patch(clientsPath + "/" + key2)
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("accessTokenTtlSeconds", 600, "refreshTokenTtlSeconds", 1209600))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessTokenTtlSeconds").value(600))
                .andExpect(jsonPath("$.refreshTokenTtlSeconds").value(1209600));

        // update: clear overrides by sending -1
        JsonNode cleared = objectMapper.readTree(
                mockMvc.perform(patch(clientsPath + "/" + key2)
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("accessTokenTtlSeconds", -1, "refreshTokenTtlSeconds", -1))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        org.junit.jupiter.api.Assertions.assertTrue(cleared.get("accessTokenTtlSeconds").isNull(),
                "accessTokenTtlSeconds should be null after clearing");
        org.junit.jupiter.api.Assertions.assertTrue(cleared.get("refreshTokenTtlSeconds").isNull(),
                "refreshTokenTtlSeconds should be null after clearing");
    }

    // --- helpers ---

    private record OrgTokens(String accessToken, String refreshToken, long expiresInSeconds, long userId) {
    }

    private String registerPlatform(String email, String slug) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "firstName", "F", "lastName", "L",
                                "email", email, "password", "password1",
                                "platformName", "Session Settings Platform", "platformSlug", slug))))
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

    private OrgTokens registerOrgUser(String boss, String platform, long orgId, String identifier,
                                      String password) throws Exception {
        MvcResult result = mockMvc.perform(post(platform + "/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "organisationId", orgId, "username", identifier,
                                "password", password, "firstName", "F", "lastName", "L"))))
                .andExpect(status().isCreated())
                .andReturn();
        return toTokens(result.getResponse().getContentAsString());
    }

    private OrgTokens loginOrg(String orgAuth, long orgId, String identifier, String password) throws Exception {
        MvcResult result = mockMvc.perform(post(orgAuth + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("organisationId", orgId, "identifier", identifier, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return toTokens(result.getResponse().getContentAsString());
    }

    private OrgTokens toTokens(String body) throws Exception {
        JsonNode node = objectMapper.readTree(body);
        return new OrgTokens(
                node.get("accessToken").asText(),
                node.get("refreshToken").asText(),
                node.get("expiresInSeconds").asLong(),
                node.get("user").get("id").asLong());
    }

    private String registerReadOnlyMember(String boss, String platform) throws Exception {
        mockMvc.perform(post(platform + "/users")
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "firstName", "R", "lastName", "O",
                                "email", "ss-readonly@nexx.io", "password", "readonly-pw"))))
                .andExpect(status().isCreated());
        MvcResult login = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "ss-readonly@nexx.io", "password", "readonly-pw"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(login.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private JsonNode decodeClaims(String token) throws Exception {
        String payload = token.split("\\.")[1];
        byte[] decoded = Base64.getUrlDecoder().decode(payload);
        return objectMapper.readTree(new String(decoded, java.nio.charset.StandardCharsets.UTF_8));
    }

    private String createClientWithOverrides(String boss, String clientsPath, String name, String type,
                                              Long accessTtl, Long refreshTtl, Integer maxSessions) throws Exception {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("name", name);
        body.put("type", type);
        if (accessTtl != null) body.put("accessTokenTtlSeconds", accessTtl);
        if (refreshTtl != null) body.put("refreshTokenTtlSeconds", refreshTtl);
        if (maxSessions != null) body.put("maxSessionsPerUser", maxSessions);
        MvcResult result = mockMvc.perform(post(clientsPath)
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("clientKey").asText();
    }

    private Client createTokenClientWithOverrides(String boss, String clientsPath, String name, String type,
                                                   Long accessTtl, Long refreshTtl, Integer maxSessions) throws Exception {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("name", name);
        body.put("type", type);
        body.put("requireAuthentication", true);
        if (accessTtl != null) body.put("accessTokenTtlSeconds", accessTtl);
        if (refreshTtl != null) body.put("refreshTokenTtlSeconds", refreshTtl);
        if (maxSessions != null) body.put("maxSessionsPerUser", maxSessions);
        MvcResult result = mockMvc.perform(post(clientsPath)
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return new Client(node.get("clientKey").asText(), node.get("token").asText());
    }

    private record Client(String clientKey, String token) {
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
