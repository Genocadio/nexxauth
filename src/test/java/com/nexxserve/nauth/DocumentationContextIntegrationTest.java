package com.nexxserve.nauth;

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
 * Public docs-context endpoint: serves the organisation configuration used to
 * render context-aware API docs, with no authentication. Regression guard for
 * the lazy-load failure — the response reads the organisation's platform slug,
 * which used to throw a LazyInitializationException outside a session.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DocumentationContextIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void docsContextIsServedWithoutAuthentication() throws Exception {
        String platformSlug = "dc1";
        String boss = registerPlatform("dc-boss@nexx.io", platformSlug);
        long orgId = createOrganisation(boss, "/" + platformSlug, "Docs Org", "docs-org");

        // no auth header — the endpoint is intentionally public
        mockMvc.perform(get("/" + platformSlug + "/organisations/" + orgId + "/docs/context"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organisation.id").value(orgId))
                .andExpect(jsonPath("$.organisation.name").value("Docs Org"))
                .andExpect(jsonPath("$.organisation.slug").value("docs-org"))
                // reads org.getPlatform().getSlug() — the lazy association that
                // used to blow up outside a transaction
                .andExpect(jsonPath("$.organisation.platformSlug").value(platformSlug))
                .andExpect(jsonPath("$.identifiers.usernameCanLogin").value(true))
                .andExpect(jsonPath("$.auth.authType").value("PASSWORD"))
                .andExpect(jsonPath("$.sessions.maxSessionsPerUser").value(5))
                .andExpect(jsonPath("$.availablePermissions").isArray());
    }

    @Test
    void docsContextReflectsConfiguredValues() throws Exception {
        String platformSlug = "dc2";
        String boss = registerPlatform("dc2-boss@nexx.io", platformSlug);
        long orgId = createOrganisation(boss, "/" + platformSlug, "Configured Org", "configured-org");

        mockMvc.perform(patch("/" + platformSlug + "/organisations/" + orgId + "/auth-config")
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("passwordMinLength", 12))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/" + platformSlug + "/organisations/" + orgId + "/docs/context"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.auth.passwordMinLength").value(12));
    }

    @Test
    void unknownOrganisationIs404() throws Exception {
        mockMvc.perform(get("/dc3/organisations/999999/docs/context"))
                .andExpect(status().isNotFound());
    }

    // --- helpers ---

    private String registerPlatform(String email, String slug) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "firstName", "F", "lastName", "L",
                                "email", email, "password", "password1",
                                "platformName", "Docs Platform", "platformSlug", slug))))
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
