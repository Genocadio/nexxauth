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
 * rules enforced by the client token filter, and per-client CORS.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OrganisationClientIntegrationTest {

    private static final String ORG_SLUG = "clients-co";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void clientLifecycle() throws Exception {
        String boss = register("client-boss@nexx.io", "Client Platform");
        String platform = createOrg(boss, ORG_SLUG);
        String clients = clientsPath(platform);

        // web client: never authenticated, no token, CORS origins stored as list
        MvcResult web = mockMvc.perform(post(clients)
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Web App", "type", "WEB",
                                "allowedOrigins", List.of("https://app.example.com")))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Web App"))
                .andExpect(jsonPath("$.type").value("WEB"))
                .andExpect(jsonPath("$.requireAuthentication").value(false))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.allowedOrigins[0]").value("https://app.example.com"))
                .andExpect(jsonPath("$.token").value(nullValue()))
                .andReturn();
        long webId = id(web);

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
        long serverId = serverJson.get("id").asLong();
        String serverToken = serverJson.get("token").asText();

        // list: both present, no token leaks
        mockMvc.perform(get(clients).header("Authorization", bearer(boss)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].token").value(nullValue()));

        mockMvc.perform(get(clients + "/" + webId).header("Authorization", bearer(boss)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Web App"));

        // update: name, origins, disable
        mockMvc.perform(patch(clients + "/" + webId)
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
        MvcResult rotated = mockMvc.perform(post(clients + "/" + serverId + "/rotate-token")
                        .header("Authorization", bearer(boss)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.token").value(not(serverToken)))
                .andReturn();
        String rotatedToken = objectMapper.readTree(rotated.getResponse().getContentAsString()).get("token").asText();

        // rotating changes the accepted token
        String users = usersPath(platform);
        mockMvc.perform(get(users)
                        .header("X-Client-Id", String.valueOf(serverId))
                        .header("Authorization", bearer(serverToken)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(users)
                        .header("X-Client-Id", String.valueOf(serverId))
                        .header("Authorization", bearer(rotatedToken)))
                .andExpect(status().isOk());

        // delete
        mockMvc.perform(delete(clients + "/" + webId).header("Authorization", bearer(boss)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(clients + "/" + webId).header("Authorization", bearer(boss)))
                .andExpect(status().isNotFound());
    }

    @Test
    void typeRulesAreEnforced() throws Exception {
        String boss = register("client-type@nexx.io", "Client Type Platform");
        String platform = createOrg(boss, ORG_SLUG);
        String clients = clientsPath(platform);

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
        long appId = id(app);

        // toggling auth on for an app issues a token once
        mockMvc.perform(patch(clients + "/" + appId)
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("requireAuthentication", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requireAuthentication").value(true))
                .andExpect(jsonPath("$.token").isNotEmpty());

        // toggling it back off clears the token
        mockMvc.perform(patch(clients + "/" + appId)
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("requireAuthentication", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requireAuthentication").value(false))
                .andExpect(jsonPath("$.token").value(nullValue()));

        // the type is immutable: an unrelated update leaves it unchanged
        mockMvc.perform(get(clients + "/" + appId).header("Authorization", bearer(boss)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("IOS"))
                .andExpect(jsonPath("$.requireAuthentication").value(false));

        // rotating a token for a non-auth client is rejected
        mockMvc.perform(post(clients + "/" + appId + "/rotate-token")
                        .header("Authorization", bearer(boss)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void accessRulesFollowClientTypeAndToken() throws Exception {
        String boss = register("client-access@nexx.io", "Client Access Platform");
        String platform = createOrg(boss, ORG_SLUG);
        String clients = clientsPath(platform);
        String users = usersPath(platform);
        String orgLogin = "/api/v1/platforms/" + platform + "/auth/login";
        String orgRegister = "/api/v1/platforms/" + platform + "/auth/register";
        String orgRefresh = "/api/v1/platforms/" + platform + "/auth/refresh";

        long webId = createClient(boss, clients, "Web", "WEB", null);
        long disabledId = createClient(boss, clients, "Disabled", "WEB", Map.of("enabled", false));
        Client server = createTokenClient(boss, clients, "Server", "SERVER");
        long noAuthAppId = createClient(boss, clients, "App", "ANDROID", null);
        Client authApp = createTokenClient(boss, clients, "App Auth", "IOS");

        // non-auth clients may only hit org login/register
        mockMvc.perform(post(orgLogin)
                        .header("X-Client-Id", String.valueOf(webId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(orgRegister)
                        .header("X-Client-Id", String.valueOf(webId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get(users).header("X-Client-Id", String.valueOf(webId)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(orgRefresh)
                        .header("X-Client-Id", String.valueOf(webId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(users).header("X-Client-Id", String.valueOf(noAuthAppId)))
                .andExpect(status().isForbidden());

        // unknown / disabled clients are rejected up front
        mockMvc.perform(get(users).header("X-Client-Id", "999999"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(users).header("X-Client-Id", "not-a-number"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(users).header("X-Client-Id", String.valueOf(disabledId)))
                .andExpect(status().isForbidden());

        // auth clients need their token
        mockMvc.perform(get(users).header("X-Client-Id", String.valueOf(server.id)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(users)
                        .header("X-Client-Id", String.valueOf(server.id))
                        .header("Authorization", bearer("nx_wrong")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(users)
                        .header("X-Client-Id", String.valueOf(server.id))
                        .header("Authorization", bearer(server.token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // an auth-capable app works the same once its auth is on
        mockMvc.perform(get(users)
                        .header("X-Client-Id", String.valueOf(authApp.id))
                        .header("Authorization", bearer(authApp.token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // an authenticated client can still hit org auth endpoints
        mockMvc.perform(post(orgLogin)
                        .header("X-Client-Id", String.valueOf(server.id))
                        .header("Authorization", bearer(server.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        // clients are scoped to their own organisation
        createOrg(boss, "other-co");
        mockMvc.perform(get("/api/v1/platforms/" + platform + "/organisations/other-co/users")
                        .header("X-Client-Id", String.valueOf(server.id))
                        .header("Authorization", bearer(server.token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void corsAppliesPerClient() throws Exception {
        String boss = register("client-cors@nexx.io", "Client Cors Platform");
        String platform = createOrg(boss, ORG_SLUG);
        String clients = clientsPath(platform);

        long webId = createClient(boss, clients, "Web", "WEB",
                Map.of("allowedOrigins", List.of("https://app.example.com")));
        long serverId = createClient(boss, clients, "Server", "SERVER",
                Map.of("allowedOrigins", List.of("https://svc.example.com")));

        // matching preflight -> 200 with CORS headers, no auth involved
        mockMvc.perform(options("/api/v1/platforms/" + platform + "/auth/login")
                        .header("X-Client-Id", String.valueOf(webId))
                        .header("Origin", "https://app.example.com")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://app.example.com"))
                .andExpect(header().string("Access-Control-Allow-Methods", containsString("POST")))
                .andExpect(header().string("Access-Control-Allow-Headers", containsString("X-Client-Id")));

        // non-matching origin -> no CORS headers
        mockMvc.perform(options("/api/v1/platforms/" + platform + "/auth/login")
                        .header("X-Client-Id", String.valueOf(webId))
                        .header("Origin", "https://evil.example.com")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));

        // actual request echoes the allowed origin
        mockMvc.perform(post("/api/v1/platforms/" + platform + "/auth/login")
                        .header("X-Client-Id", String.valueOf(webId))
                        .header("Origin", "https://app.example.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://app.example.com"));

        // CORS applies to server clients too (trusted origins)
        mockMvc.perform(options("/api/v1/platforms/" + platform + "/organisations/" + ORG_SLUG + "/users")
                        .header("X-Client-Id", String.valueOf(serverId))
                        .header("Origin", "https://svc.example.com")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://svc.example.com"));
    }

    // --- helpers ---

    private String register(String email, String platformName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
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

    /** Creates the org and returns the boss's platform slug. */
    private String createOrg(String boss, String slug) throws Exception {
        String platformSlug = objectMapper.readTree(
                        mockMvc.perform(get("/api/v1/auth/me")
                                        .header("Authorization", bearer(boss)))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse().getContentAsString())
                .get("platform").get("slug").asText();
        mockMvc.perform(post("/api/v1/platforms/" + platformSlug + "/organisations")
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", slug, "slug", slug))))
                .andExpect(status().isCreated());
        return platformSlug;
    }

    private long createClient(String boss, String clients, String name, String type,
                              Map<String, Object> extra) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("type", type);
        if (extra != null) {
            body.putAll(extra);
        }
        return id(mockMvc.perform(post(clients)
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
        return new Client(node.get("id").asLong(), node.get("token").asText());
    }

    private record Client(long id, String token) {
    }

    private String clientsPath(String platformSlug) {
        return "/api/v1/platforms/" + platformSlug + "/organisations/" + ORG_SLUG + "/clients";
    }

    private String usersPath(String platformSlug) {
        return "/api/v1/platforms/" + platformSlug + "/organisations/" + ORG_SLUG + "/users";
    }

    private long id(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
