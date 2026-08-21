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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PlatformSlugIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void omittedSlugIsDerivedFromName() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "firstName", "A",
                                "lastName", "B",
                                "email", "derived@nexx.io",
                                "password", "password1",
                                "platformName", "The Big Company!"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.platform.slug").value("the-big-company"));
    }

    @Test
    void explicitSlugIsUsedAsIs() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "firstName", "A",
                                "lastName", "B",
                                "email", "explicit@nexx.io",
                                "password", "password1",
                                "platformName", "Custom Name",
                                "platformSlug", "my-custom-slug"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.platform.slug").value("my-custom-slug"));
    }

    @Test
    void takenExplicitSlugIsRejectedWith409() throws Exception {
        String register = json(Map.of(
                "firstName", "A",
                "lastName", "B",
                "email", "taken@nexx.io",
                "password", "password1",
                "platformName", "Taken Name",
                "platformSlug", "shared-slug"));

        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(register))
                .andExpect(status().isCreated());

        // Second platform asking for the same explicit slug is rejected, never renamed.
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "firstName", "C",
                                "lastName", "D",
                                "email", "taken2@nexx.io",
                                "password", "password1",
                                "platformName", "Other Name",
                                "platformSlug", "shared-slug"))))
                .andExpect(status().isConflict());
    }

    @Test
    void duplicateNameGetsUniqueAutoSlug() throws Exception {
        String register = json(Map.of(
                "firstName", "A",
                "lastName", "B",
                "email", "dupname@nexx.io",
                "password", "password1",
                "platformName", "Same Name"));

        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(register))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.platform.slug").value("same-name"));

        // Same name again: the auto-derived slug is deduped, not rejected.
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "firstName", "C",
                                "lastName", "D",
                                "email", "dupname2@nexx.io",
                                "password", "password1",
                                "platformName", "Same Name"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.platform.slug").value("same-name-2"));
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
