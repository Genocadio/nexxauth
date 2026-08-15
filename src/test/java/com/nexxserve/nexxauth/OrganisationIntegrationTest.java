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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OrganisationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void fullOrganisationLifecycle() throws Exception {
        String boss = register("org-boss@nexx.io", "Org Platform");

        // create: slug derived from name
        mockMvc.perform(post("/api/v1/platforms/org-platform/organisations")
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Nexx Labs", "description", "R&D"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("nexx-labs"))
                .andExpect(jsonPath("$.name").value("Nexx Labs"));

        // create with explicit slug + second organisation
        mockMvc.perform(post("/api/v1/platforms/org-platform/organisations")
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Nexx Services", "slug", "services"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("services"));

        // a platform can have many organisations
        mockMvc.perform(get("/api/v1/platforms/org-platform/organisations")
                        .header("Authorization", bearer(boss)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        // get one
        mockMvc.perform(get("/api/v1/platforms/org-platform/organisations/nexx-labs")
                        .header("Authorization", bearer(boss)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("R&D"));

        // partial update: name + description
        mockMvc.perform(patch("/api/v1/platforms/org-platform/organisations/nexx-labs")
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Nexx Labs HQ", "description", "HQ"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Nexx Labs HQ"))
                .andExpect(jsonPath("$.description").value("HQ"));

        // rename slug
        mockMvc.perform(patch("/api/v1/platforms/org-platform/organisations/nexx-labs")
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("slug", "hq"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("hq"));

        // old slug 404s, new slug resolves
        mockMvc.perform(get("/api/v1/platforms/org-platform/organisations/nexx-labs")
                        .header("Authorization", bearer(boss)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/platforms/org-platform/organisations/hq")
                        .header("Authorization", bearer(boss)))
                .andExpect(status().isOk());

        // delete
        mockMvc.perform(delete("/api/v1/platforms/org-platform/organisations/hq")
                        .header("Authorization", bearer(boss)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/platforms/org-platform/organisations/hq")
                        .header("Authorization", bearer(boss)))
                .andExpect(status().isNotFound());
    }

    @Test
    void readOnlyMembersCanReadButNotWrite() throws Exception {
        String boss = register("org-ro-boss@nexx.io", "Ro Platform");
        mockMvc.perform(post("/api/v1/platforms/ro-platform/organisations")
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Readable"))))
                .andExpect(status().isCreated());

        MvcResult add = mockMvc.perform(post("/api/v1/platforms/ro-platform/users")
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "firstName", "R",
                                "lastName", "O",
                                "email", "ro@nexx.io",
                                "password", "password1"))))
                .andExpect(status().isCreated())
                .andReturn();
        long memberId = objectMapper.readTree(add.getResponse().getContentAsString()).get("id").asLong();

        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "ro@nexx.io", "password", "password1"))))
                .andExpect(status().isOk())
                .andReturn();
        String member = objectMapper.readTree(login.getResponse().getContentAsString()).get("accessToken").asText();

        // reads allowed for any member
        mockMvc.perform(get("/api/v1/platforms/ro-platform/organisations")
                        .header("Authorization", bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        // writes denied for read-only members
        mockMvc.perform(post("/api/v1/platforms/ro-platform/organisations")
                        .header("Authorization", bearer(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Nope"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/platforms/ro-platform/organisations/readable")
                        .header("Authorization", bearer(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Nope"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/platforms/ro-platform/organisations/readable")
                        .header("Authorization", bearer(member)))
                .andExpect(status().isForbidden());
    }

    @Test
    void slugConflictsAndCrossPlatformIsolation() throws Exception {
        String bossA = register("iso-a@nexx.io", "Isolation A");
        String bossB = register("iso-b@nexx.io", "Isolation B");

        // same slug is fine in different platforms
        mockMvc.perform(post("/api/v1/platforms/isolation-a/organisations")
                        .header("Authorization", bearer(bossA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Shared", "slug", "shared"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/platforms/isolation-b/organisations")
                        .header("Authorization", bearer(bossB))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Shared", "slug", "shared"))))
                .andExpect(status().isCreated());

        // duplicate slug within the same platform conflicts
        mockMvc.perform(post("/api/v1/platforms/isolation-a/organisations")
                        .header("Authorization", bearer(bossA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Shared Again", "slug", "shared"))))
                .andExpect(status().isConflict());

        // a member of another platform cannot see or touch these organisations
        mockMvc.perform(get("/api/v1/platforms/isolation-a/organisations")
                        .header("Authorization", bearer(bossB)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/platforms/isolation-a/organisations/shared")
                        .header("Authorization", bearer(bossB)))
                .andExpect(status().isForbidden());

        // unknown platform / organisation
        mockMvc.perform(get("/api/v1/platforms/does-not-exist/organisations")
                        .header("Authorization", bearer(bossA)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/platforms/isolation-a/organisations/nope")
                        .header("Authorization", bearer(bossA)))
                .andExpect(status().isNotFound());
    }

    @Test
    void validationIsEnforced() throws Exception {
        String boss = register("org-valid@nexx.io", "Valid Platform");

        mockMvc.perform(post("/api/v1/platforms/valid-platform/organisations")
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"));

        mockMvc.perform(post("/api/v1/platforms/valid-platform/organisations")
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Bad Slug", "slug", "UPPER_CASE"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("slug"));
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

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
