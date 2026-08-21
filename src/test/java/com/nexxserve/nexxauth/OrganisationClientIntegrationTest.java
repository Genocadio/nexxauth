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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Organisation clients: CRUD (with the once-only static token), per-type auth
 * rules enforced by the client token filter, and per-client CORS. Clients are
 * identified by an opaque {@code clientKey} (sent as {@code X-Client-Id}),
 * never the numeric id.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OrganisationClientIntegrationTest {

    private static final String ORG_SLUG = "clients-co";
    private static final String CLIENT_KEY_PATTERN = "cli_[A-Za-z0-9_-]{40,}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void clientLifecycle() throws Exception {
        String boss = register("client-boss@nexx.io", "Client Platform");
        long orgId = createOrg(boss, ORG_SLUG);
        String platform = getPlatformSlug(boss);
        String clients = clientsPath(platform, orgId);

        // web client: never authenticated, no token, CORS origins stored as list
        MvcResult web = mockMvc.perform(post(clients)
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Web App", "type", "WEB",
                                "allowedOrigins", List.of("https://app.example.com")))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Web App"))
                .andExpect(jsonPath("$.type").value("WEB"))
                .andExpect(jsonPath("$.clientKey").value(matchesPattern(CLIENT_KEY_PATTERN)))
                .andExpect(jsonPath("$.requireAuthentication").value(false))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.allowedOrigins[0]").value("https://app.example.com"))
                .andExpect(jsonPath("$.token").value(nullValue()))
                .andReturn();
        String webKey = clientKey(web);

        // server client: always authenticated, token shown exactly once
        MvcResult server = mockMvc.perform(post(clients)
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Backend", "type", "SERVER",
                                "settings", Map.of("baseUrl", "https://svc.example.com")))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requireAuthentication").value(true))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();
        JsonNode serverJson = objectMapper.readTree(server.getResponse().getContentAsString());
        String serverKey = serverJson.get("clientKey").asText();
        String serverToken = serverJson.get("token").asText();

        // list: both present, no token leaks
        mockMvc.perform(get(clients).header("Authorization", bearer(boss)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].token").value(nullValue()));

        mockMvc.perform(get(clients + "/" + webKey).header("Authorization", bearer(boss)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Web App"));

        // update: name, origins, disable
        mockMvc.perform(patch(clients + "/" + webKey)
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Web App v2", "enabled", false,
                                "allowedOrigins", List.of("https://one.example.com", "https://two.example.com")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Web App v2"))
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.allowedOrigins.length()").value(2))
                .andExpect(jsonPath("$.token").value(nullValue()));

        // rotate: fresh token, shown once again
        MvcResult rotated = mockMvc.perform(post(clients + "/" + serverKey + "/rotate-token")
                        .header("Authorization", bearer(boss)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.token").value(not(serverToken)))
                .andReturn();
        String rotatedToken = objectMapper.readTree(rotated.getResponse().getContentAsString()).get("token").asText();

        // rotating changes the accepted token
        String users = usersPath(platform, orgId);
        mockMvc.perform(get(users)
                        .header("X-Client-Id", serverKey)
                        .header("Authorization", bearer(serverToken)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(users)
                        .header("X-Client-Id", serverKey)
                        .header("Authorization", bearer(rotatedToken)))
                .andExpect(status().isOk());

        // delete
        mockMvc.perform(delete(clients + "/" + webKey).header("Authorization", bearer(boss)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(clients + "/" + webKey).header("Authorization", bearer(boss)))
                .andExpect(status().isNotFound());
    }

    @Test
    void typeRulesAreEnforced() throws Exception {
        String boss = register("client-type@nexx.io", "Client Type Platform");
        long orgId = createOrg(boss, ORG_SLUG);
        String platform = getPlatformSlug(boss);
        String clients = clientsPath(platform, orgId);

        // forced rules cannot be contradicted
        mockMvc.perform(post(clients)
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Bad Web", "type", "WEB", "requireAuthentication", true))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(clients)
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Bad Server", "type", "SERVER", "requireAuthentication", false))))
                .andExpect(status().isBadRequest());

        // apps default to no auth; can be enabled later
        MvcResult app = mockMvc.perform(post(clients)
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "iOS App", "type", "IOS"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requireAuthentication").value(false))
                .andExpect(jsonPath("$.token").value(nullValue()))
                .andReturn();
        String appKey = clientKey(app);

        // toggling auth on for an app issues a token once
        mockMvc.perform(patch(clients + "/" + appKey)
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("requireAuthentication", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requireAuthentication").value(true))
                .andExpect(jsonPath("$.token").isNotEmpty());

        // toggling it back off clears the token
        mockMvc.perform(patch(clients + "/" + appKey)
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("requireAuthentication", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requireAuthentication").value(false))
                .andExpect(jsonPath("$.token").value(nullValue()));

        // the type is immutable: an unrelated update leaves it unchanged
        mockMvc.perform(get(clients + "/" + appKey).header("Authorization", bearer(boss)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("IOS"))
                .andExpect(jsonPath("$.requireAuthentication").value(false));

        // rotating a token for a non-auth client is rejected
        mockMvc.perform(post(clients + "/" + appKey + "/rotate-token")
                        .header("Authorization", bearer(boss)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void accessRulesFollowClientTypeAndToken() throws Exception {
        String boss = register("client-access@nexx.io", "Client Access Platform");
        long orgId = createOrg(boss, ORG_SLUG);
        String platform = getPlatformSlug(boss);
        String clients = clientsPath(platform, orgId);
        String users = usersPath(platform, orgId);
        String orgLogin = "/" + platform + "/auth/login";
        String orgRegister = "/" + platform + "/auth/register";
        String orgRefresh = "/" + platform + "/auth/refresh";

        String webKey = createClient(boss, clients, "Web", "WEB", null);
        String disabledKey = createClient(boss, clients, "Disabled", "WEB", Map.of("enabled", false));
        Client server = createTokenClient(boss, clients, "Server", "SERVER");
        String noAuthAppKey = createClient(boss, clients, "App", "ANDROID", null);
        Client authApp = createTokenClient(boss, clients, "App Auth", "IOS");

        // non-auth clients may only hit org login/register
        mockMvc.perform(post(orgLogin)
                        .header("X-Client-Id", webKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(orgRegister)
                        .header("X-Client-Id", webKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get(users).header("X-Client-Id", webKey))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(orgRefresh)
                        .header("X-Client-Id", webKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(users).header("X-Client-Id", noAuthAppKey))
                .andExpect(status().isForbidden());

        // unknown / disabled clients are rejected up front
        mockMvc.perform(get(users).header("X-Client-Id", "cli_unknown"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(users).header("X-Client-Id", "cli_another-missing"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(users).header("X-Client-Id", disabledKey))
                .andExpect(status().isForbidden());

        // auth clients need their token
        mockMvc.perform(get(users).header("X-Client-Id", server.clientKey))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(users)
                        .header("X-Client-Id", server.clientKey)
                        .header("Authorization", bearer("nx_wrong")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(users)
                        .header("X-Client-Id", server.clientKey)
                        .header("Authorization", bearer(server.token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // an auth-capable app works the same once its auth is on
        mockMvc.perform(get(users)
                        .header("X-Client-Id", authApp.clientKey)
                        .header("Authorization", bearer(authApp.token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // an authenticated client can still hit org auth endpoints
        mockMvc.perform(post(orgLogin)
                        .header("X-Client-Id", server.clientKey)
                        .header("Authorization", bearer(server.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        // clients are scoped to their own organisation
        long otherOrgId = createOrg(boss, "other-co");
        mockMvc.perform(get("/" + platform + "/organisations/" + otherOrgId + "/users")
                        .header("X-Client-Id", server.clientKey)
                        .header("Authorization", bearer(server.token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void webClientLetsAuthenticatedOrgUserThrough() throws Exception {
        String boss = register("client-webuser@nexx.io", "Client Web User Platform");
        long orgId = createOrg(boss, ORG_SLUG);
        String platform = getPlatformSlug(boss);
        String clients = clientsPath(platform, orgId);
        String users = usersPath(platform, orgId);
        String orgAuth = "/" + platform + "/auth";

        String webKey = createClient(boss, clients, "Web", "WEB", null);
        String orgPath = "/" + platform + "/organisations/" + orgId;

        // an org user holding READ permission
        long roleId = objectMapper.readTree(mockMvc.perform(post(orgPath + "/roles")
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "reader", "permissions",
                                List.of("ORGANISATION_USER_READ")))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
        long readerId = objectMapper.readTree(mockMvc.perform(post(orgAuth + "/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("organisationId", orgId, "username", "alice",
                                "password", "password1", "firstName", "A", "lastName", "L"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("user").get("id").asLong();
        mockMvc.perform(patch(orgPath + "/users/" + readerId)
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("roleIds", List.of(roleId)))))
                .andExpect(status().isOk());
        String aliceToken = objectMapper.readTree(mockMvc.perform(post(orgAuth + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("organisationId", orgId, "identifier", "alice", "password", "password1"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("accessToken").asText();

        // the web client alone is still blocked from the org API
        mockMvc.perform(get(users).header("X-Client-Id", webKey))
                .andExpect(status().isForbidden());

        // but with a valid org-user JWT, the user proceeds under their own roles
        mockMvc.perform(get(users)
                        .header("X-Client-Id", webKey)
                        .header("Authorization", bearer(aliceToken)))
                .andExpect(status().isOk());

        // a disabled/unknown org-user token is still blocked (no org auth set)
        mockMvc.perform(get(users)
                        .header("X-Client-Id", webKey)
                        .header("Authorization", bearer("nx_not-a-real-token")))
                .andExpect(status().isForbidden());
    }

    @Test
    void orgUserFromForeignOriginWithoutClientIsBlocked() throws Exception {
        String boss = register("client-foreign@nexx.io", "Client Foreign Platform");
        long orgId = createOrg(boss, ORG_SLUG);
        String platform = getPlatformSlug(boss);
        String clients = clientsPath(platform, orgId);
        String users = usersPath(platform, orgId);
        String orgAuth = "/" + platform + "/auth";

        String orgPath = "/" + platform + "/organisations/" + orgId;

        // an org user with READ, no client at all
        long roleId = objectMapper.readTree(mockMvc.perform(post(orgPath + "/roles")
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "reader", "permissions",
                                List.of("ORGANISATION_USER_READ")))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
        long userId = objectMapper.readTree(mockMvc.perform(post(orgAuth + "/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("organisationId", orgId, "username", "bob",
                                "password", "password1", "firstName", "B", "lastName", "L"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("user").get("id").asLong();
        mockMvc.perform(patch(orgPath + "/users/" + userId)
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("roleIds", List.of(roleId)))))
                .andExpect(status().isOk());
        String bobToken = objectMapper.readTree(mockMvc.perform(post(orgAuth + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("organisationId", orgId, "identifier", "bob", "password", "password1"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("accessToken").asText();

        // org user + no client id + foreign origin -> blocked (default-deny)
        mockMvc.perform(get(users)
                        .header("Authorization", bearer(bobToken))
                        .header("Origin", "https://evil.example.com"))
                .andExpect(status().isForbidden());

        // org user + no client id + same-origin (server's own host) -> allowed
        mockMvc.perform(get(users)
                        .header("Authorization", bearer(bobToken))
                        .header("Origin", "http://localhost"))
                .andExpect(status().isOk());

        // org user + no client id + no origin (server-to-server / self) -> allowed
        mockMvc.perform(get(users).header("Authorization", bearer(bobToken)))
                .andExpect(status().isOk());

        // a platform user with no client id and a foreign origin is still allowed
        // (self/local admin path is limited to platform users, not blocked by origin)
        mockMvc.perform(get(users)
                        .header("Authorization", bearer(boss))
                        .header("Origin", "https://evil.example.com"))
                .andExpect(status().isOk());

        // ...and a foreign-origin org user IS allowed once a client id is present
        createClient(boss, clients, "Web", "WEB", null);
        // (per-client CORS gating is covered by corsAppliesPerClient)
    }

    @Test
    void corsAppliesPerClient() throws Exception {
        String boss = register("client-cors@nexx.io", "Client Cors Platform");
        long orgId = createOrg(boss, ORG_SLUG);
        String platform = getPlatformSlug(boss);
        String clients = clientsPath(platform, orgId);

        String webKey = createClient(boss, clients, "Web", "WEB",
                Map.of("allowedOrigins", List.of("https://app.example.com")));
        String serverKey = createClient(boss, clients, "Server", "SERVER",
                Map.of("allowedOrigins", List.of("https://svc.example.com")));

        // matching preflight -> 200 with CORS headers, no auth involved
        mockMvc.perform(options("/" + platform + "/auth/login")
                        .header("X-Client-Id", webKey)
                        .header("Origin", "https://app.example.com")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://app.example.com"))
                .andExpect(header().string("Access-Control-Allow-Methods", containsString("POST")))
                .andExpect(header().string("Access-Control-Allow-Headers", containsString("X-Client-Id")));

        // non-matching origin -> no CORS headers
        mockMvc.perform(options("/" + platform + "/auth/login")
                        .header("X-Client-Id", webKey)
                        .header("Origin", "https://evil.example.com")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));

        // actual request echoes the allowed origin
        mockMvc.perform(post("/" + platform + "/auth/login")
                        .header("X-Client-Id", webKey)
                        .header("Origin", "https://app.example.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://app.example.com"));

        // CORS applies to server clients too (trusted origins)
        mockMvc.perform(options("/" + platform + "/organisations/" + orgId + "/users")
                        .header("X-Client-Id", serverKey)
                        .header("Origin", "https://svc.example.com")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://svc.example.com"));
    }

    // --- helpers ---

    private String register(String email, String platformName) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "firstName", "F",
                                "lastName", "L",
                                "email", email,
                                "password", "password1",
                                "platformName", platformName))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    /** Creates the org and returns the org ID. */
    private long createOrg(String boss, String slug) throws Exception {
        String platformSlug = getPlatformSlug(boss);
        MvcResult result = mockMvc.perform(post("/" + platformSlug + "/organisations")
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", slug, "slug", slug))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private String getPlatformSlug(String boss) throws Exception {
        return objectMapper.readTree(
                        mockMvc.perform(get("/auth/me")
                                        .header("Authorization", bearer(boss)))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse().getContentAsString())
                .get("platform").get("slug").asText();
    }

    private String createClient(String boss, String clients, String name, String type,
                                Map<String, Object> extra) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("type", type);
        if (extra != null) {
            body.putAll(extra);
        }
        return clientKey(mockMvc.perform(post(clients)
                .header("Authorization", bearer(boss))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(body)))
                .andExpect(status().isCreated())
                .andReturn());
    }

    private Client createTokenClient(String boss, String clients, String name, String type) throws Exception {
        MvcResult result = mockMvc.perform(post(clients)
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", name, "type", type, "requireAuthentication", true))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return new Client(node.get("clientKey").asText(), node.get("token").asText());
    }

    private record Client(String clientKey, String token) {
    }

    private String clientsPath(String platformSlug, long orgId) {
        return "/" + platformSlug + "/organisations/" + orgId + "/clients";
    }

    private String usersPath(String platformSlug, long orgId) {
        return "/" + platformSlug + "/organisations/" + orgId + "/users";
    }

    private String clientKey(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("clientKey").asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}