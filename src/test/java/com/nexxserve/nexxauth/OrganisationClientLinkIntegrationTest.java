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
 * Integration tests for organisation client links: CRUD operations,
 * per-link CORS enforcement, and limit-source origin restrictions.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OrganisationClientLinkIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void linkCrudLifecycle() throws Exception {
        String boss = register("link-crud@nexx.io", "Link CRUD Platform");
        long orgId = createOrg(boss, "link-crud");
        String platform = getPlatformSlug(boss);
        String webKey = createClient(boss, platform, orgId, "Web", "WEB");
        String links = linksPath(platform, orgId, webKey);

        // create
        MvcResult created = mockMvc.perform(post(links)
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("origin", "https://app.example.com",
                                "allowCors", true, "limitSource", false))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.origin").value("https://app.example.com"))
                .andExpect(jsonPath("$.allowCors").value(true))
                .andExpect(jsonPath("$.limitSource").value(false))
                .andReturn();
        long linkId = linkId(created);

        // read
        mockMvc.perform(get(links + "/" + linkId).header("Authorization", bearer(boss)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.origin").value("https://app.example.com"));

        // list
        mockMvc.perform(get(links).header("Authorization", bearer(boss)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        // update
        mockMvc.perform(patch(links + "/" + linkId)
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("limitSource", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.limitSource").value(true));

        // duplicate origin rejected
        mockMvc.perform(post(links)
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("origin", "https://app.example.com"))))
                .andExpect(status().isConflict());

        // delete
        mockMvc.perform(delete(links + "/" + linkId).header("Authorization", bearer(boss)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(links + "/" + linkId).header("Authorization", bearer(boss)))
                .andExpect(status().isNotFound());
    }

    @Test
    void corsAppliesPerLink() throws Exception {
        String boss = register("link-cors@nexx.io", "Link Cors Platform");
        long orgId = createOrg(boss, "link-cors");
        String platform = getPlatformSlug(boss);
        String webKey = createClient(boss, platform, orgId, "Web", "WEB");
        String links = linksPath(platform, orgId, webKey);

        // Add a link with allowCors=true
        mockMvc.perform(post(links)
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("origin", "https://app.example.com", "allowCors", true))))
                .andExpect(status().isCreated());

        // preflight with matching origin -> CORS headers
        mockMvc.perform(options("/" + platform + "/auth/login")
                        .header("X-Client-Id", webKey)
                        .header("Origin", "https://app.example.com")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://app.example.com"))
                .andExpect(header().string("Access-Control-Allow-Methods", containsString("POST")));

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
    }

    @Test
    void limitSourceRejectsUntrustedOrigin() throws Exception {
        String boss = register("link-limit@nexx.io", "Link Limit Platform");
        long orgId = createOrg(boss, "link-limit");
        String platform = getPlatformSlug(boss);
        String orgAuth = "/" + platform + "/auth";
        String users = usersPath(platform, orgId);
        String orgPath = "/" + platform + "/organisations/" + orgId;

        // Create web client with a link that has limitSource=true
        String webKey = createClient(boss, platform, orgId, "Web", "WEB");
        String links = linksPath(platform, orgId, webKey);
        mockMvc.perform(post(links)
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("origin", "https://trusted.example.com",
                                "allowCors", true, "limitSource", true))))
                .andExpect(status().isCreated());

        // Create a role, register a user, assign role, get org token
        long roleId = objectMapper.readTree(
                mockMvc.perform(post(orgPath + "/roles")
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "reader", "permissions",
                                java.util.List.of("ORGANISATION_USER_READ")))))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString()
                ).get("id").asLong();
        long userId = objectMapper.readTree(
                mockMvc.perform(post(orgAuth + "/register")
                        .header("X-Client-Id", webKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("organisationId", orgId, "username", "testuser",
                                "password", "password1", "firstName", "T", "lastName", "U"))))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString()
                ).get("user").get("id").asLong();
        mockMvc.perform(patch(orgPath + "/users/" + userId)
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("roleIds", java.util.List.of(roleId)))))
                .andExpect(status().isOk());
        String orgUserToken = objectMapper.readTree(
                mockMvc.perform(post(orgAuth + "/login")
                        .header("X-Client-Id", webKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("organisationId", orgId, "identifier", "testuser",
                                "password", "password1"))))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString()
                ).get("accessToken").asText();

        // Trusted origin -> allowed (org user JWT via web client)
        mockMvc.perform(get(users)
                        .header("X-Client-Id", webKey)
                        .header("Authorization", bearer(orgUserToken))
                        .header("Origin", "https://trusted.example.com"))
                .andExpect(status().isOk());

        // Untrusted origin -> 403
        mockMvc.perform(get(users)
                        .header("X-Client-Id", webKey)
                        .header("Authorization", bearer(orgUserToken))
                        .header("Origin", "https://evil.example.com"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Origin not allowed by source restriction"));
    }

    @Test
    void limitSourceWithoutCorsIsRejected() throws Exception {
        String boss = register("link-validate@nexx.io", "Link Validate Platform");
        long orgId = createOrg(boss, "link-validate");
        String platform = getPlatformSlug(boss);
        String webKey = createClient(boss, platform, orgId, "Web", "WEB");
        String links = linksPath(platform, orgId, webKey);

        // limitSource=true + allowCors=false -> 400
        mockMvc.perform(post(links)
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("origin", "https://app.example.com",
                                "allowCors", false, "limitSource", true))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Cannot enable limit source when CORS is disabled")));
    }

    @Test
    void turningCorsOffForcesLimitSourceOff() throws Exception {
        String boss = register("link-toggle@nexx.io", "Link Toggle Platform");
        long orgId = createOrg(boss, "link-toggle");
        String platform = getPlatformSlug(boss);
        String webKey = createClient(boss, platform, orgId, "Web", "WEB");
        String links = linksPath(platform, orgId, webKey);

        // Create a link with both enabled
        MvcResult created = mockMvc.perform(post(links)
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("origin", "https://app.example.com",
                                "allowCors", true, "limitSource", true))))
                .andExpect(status().isCreated())
                .andReturn();
        long linkId = linkId(created);

        // Turn CORS off -> limit source is forced off (backend rejects the combo)
        mockMvc.perform(patch(links + "/" + linkId)
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("allowCors", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowCors").value(false))
                .andExpect(jsonPath("$.limitSource").value(false));
    }

    @Test
    void preflightWithoutClientIdAnswersForTrustedLink() throws Exception {
        String boss = register("link-preflight@nexx.io", "Link Preflight Platform");
        long orgId = createOrg(boss, "link-preflight");
        String platform = getPlatformSlug(boss);
        String webKey = createClient(boss, platform, orgId, "Web", "WEB");
        String links = linksPath(platform, orgId, webKey);

        // Add a link with allowCors=true
        mockMvc.perform(post(links)
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("origin", "https://app.example.com", "allowCors", true))))
                .andExpect(status().isCreated());

        // Preflight without X-Client-Id, trusted origin -> 200 with CORS headers
        mockMvc.perform(options("/" + platform + "/auth/login")
                        .header("Origin", "https://app.example.com")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://app.example.com"))
                .andExpect(header().string("Access-Control-Allow-Methods", containsString("POST")));

        // Preflight without X-Client-Id, unknown origin -> no CORS headers
        mockMvc.perform(options("/" + platform + "/auth/login")
                        .header("Origin", "https://evil.example.com")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    // --- helpers ---

    private String register(String email, String platformName) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "firstName", "F", "lastName", "L",
                                "email", email, "password", "password1",
                                "platformName", platformName))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

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

    private String createClient(String boss, String platform, long orgId,
                                String name, String type) throws Exception {
        String clients = "/" + platform + "/organisations/" + orgId + "/clients";
        MvcResult result = mockMvc.perform(post(clients)
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", name, "type", type))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("clientKey").asText();
    }

    private String linksPath(String platform, long orgId, String clientKey) {
        return "/" + platform + "/organisations/" + orgId + "/clients/" + clientKey + "/links";
    }

    private String usersPath(String platform, long orgId) {
        return "/" + platform + "/organisations/" + orgId + "/users";
    }

    private long linkId(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
