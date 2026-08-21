package com.nexxserve.nauth;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The single slug-suggestions endpoint: public (rate-limited) for platforms,
 * authenticated (and platform-scoped) for organisations. Verifies derivation
 * from a name, availability of typed candidates, and the numbered variants.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SlugSuggestionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void platformSuggestionsArePublicAndDerivedFromName() throws Exception {
        mockMvc.perform(get("/slug-suggestions")
                        .param("type", "PLATFORM")
                        .param("name", "Acme Labs!"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("PLATFORM"))
                .andExpect(jsonPath("$.candidate.slug").value("acme-labs"))
                .andExpect(jsonPath("$.candidate.available").value(true))
                .andExpect(jsonPath("$.suggestions[0].slug").value("acme-labs"))
                .andExpect(jsonPath("$.suggestions[0].available").value(true))
                .andExpect(jsonPath("$.suggestions[1].slug").value("acme-labs-2"));
    }

    @Test
    void platformCandidateReflectsExistingSlugAndOffersVariants() throws Exception {
        register("slug-platform@nexx.io", "Slug Platform");

        mockMvc.perform(get("/slug-suggestions")
                        .param("type", "PLATFORM")
                        .param("name", "Slug Platform"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidate.slug").value("slug-platform"))
                .andExpect(jsonPath("$.candidate.available").value(false))
                .andExpect(jsonPath("$.suggestions[0].available").value(false))
                .andExpect(jsonPath("$.suggestions[1].slug").value("slug-platform-2"))
                .andExpect(jsonPath("$.suggestions[1].available").value(true));
    }

    @Test
    void platformTypedSlugIsValidatedAndChecked() throws Exception {
        mockMvc.perform(get("/slug-suggestions")
                        .param("type", "PLATFORM")
                        .param("slug", "my-custom"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidate.slug").value("my-custom"))
                .andExpect(jsonPath("$.candidate.available").value(true));

        // invalid pattern / overlong -> 400, never a 200
        mockMvc.perform(get("/slug-suggestions")
                        .param("type", "PLATFORM")
                        .param("slug", "UPPER_CASE"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/slug-suggestions")
                        .param("type", "PLATFORM")
                        .param("slug", "a".repeat(101)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void organisationSuggestionsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/slug-suggestions")
                        .param("type", "ORGANISATION")
                        .param("platformSlug", "some-platform")
                        .param("name", "Acme"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void organisationSuggestionsAreScopedToThePlatform() throws Exception {
        String boss = register("slug-org-boss@nexx.io", "Org Slug Platform");

        mockMvc.perform(get("/slug-suggestions")
                        .param("type", "ORGANISATION")
                        .param("platformSlug", "org-slug-platform")
                        .param("name", "Nexx Labs")
                        .header("Authorization", bearer(boss)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("ORGANISATION"))
                .andExpect(jsonPath("$.candidate.slug").value("nexx-labs"))
                .andExpect(jsonPath("$.candidate.available").value(true));

        // creating an organisation makes the derived slug unavailable
        mockMvc.perform(post("/org-slug-platform/organisations")
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Nexx Labs"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/slug-suggestions")
                        .param("type", "ORGANISATION")
                        .param("platformSlug", "org-slug-platform")
                        .param("name", "Nexx Labs")
                        .header("Authorization", bearer(boss)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidate.available").value(false))
                .andExpect(jsonPath("$.suggestions[1].slug").value("nexx-labs-2"))
                .andExpect(jsonPath("$.suggestions[1].available").value(true));

        // a member of another platform cannot probe this platform's slugs
        String stranger = register("slug-stranger@nexx.io", "Stranger Platform");
        mockMvc.perform(get("/slug-suggestions")
                        .param("type", "ORGANISATION")
                        .param("platformSlug", "org-slug-platform")
                        .param("name", "Nexx Labs")
                        .header("Authorization", bearer(stranger)))
                .andExpect(status().isForbidden());

        // missing / unknown platform
        mockMvc.perform(get("/slug-suggestions")
                        .param("type", "ORGANISATION")
                        .param("name", "Nexx Labs")
                        .header("Authorization", bearer(boss)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/slug-suggestions")
                        .param("type", "ORGANISATION")
                        .param("platformSlug", "nope")
                        .param("name", "Nexx Labs")
                        .header("Authorization", bearer(boss)))
                .andExpect(status().isNotFound());
    }

    @Test
    void requiresNameOrSlug() throws Exception {
        mockMvc.perform(get("/slug-suggestions").param("type", "PLATFORM"))
                .andExpect(status().isBadRequest());
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

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
