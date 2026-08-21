package com.nexxserve.nexxauth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Organisation-defined user fields: RBAC-gated config CRUD, per-user metadata
 * round-trips (typed + normalized values), login-by-login-enabled-field, and
 * the uniqueness/type-change guard rails.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OrganisationUserFieldIntegrationTest {

    private static final String[] SLUGS = {"ouf1", "ouf2", "ouf3", "ouf4", "ouf5", "ouf6"};
    private static final String ORG_SLUG = "ouf-org";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void fieldCrudMetadataRoundTripAndValidation() throws Exception {
        String platform = "/" + SLUGS[0];
        String orgAuth = platform + "/auth";
        String boss = registerPlatform("ouf-roundtrip@nexx.io", SLUGS[0]);
        long orgId = createOrganisation(boss, platform);
        String org = platform + "/organisations/" + orgId;

        createField(boss, org, "employee-id", "STRING", true);
        createField(boss, org, "active", "BOOLEAN", false);
        createField(boss, org, "joined", "DATE", false);
        createField(boss, org, "score", "NUMBER", false);

        mockMvc.perform(get(org + "/user-fields").header("Authorization", bearer(boss)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].key").value("active"))
                .andExpect(jsonPath("$[0].loginEnabled").value(false));

        long henry = registerOrgUser(orgAuth, orgId, "henry", "orgpass1");

        // boss sets metadata on henry
        mockMvc.perform(patch(org + "/users/" + henry)
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("metadata", Map.of(
                                "employee-id", "EMP123",
                                "active", "true",
                                "joined", "2026-01-05",
                                "score", "1.50")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metadata['employee-id']").value("EMP123"))
                .andExpect(jsonPath("$.metadata.active").value("true"))
                .andExpect(jsonPath("$.metadata.joined").value("2026-01-05"))
                .andExpect(jsonPath("$.metadata.score").value("1.5"));

        // persisted values appear on get and on the org user's own profile
        mockMvc.perform(get(org + "/users/" + henry).header("Authorization", bearer(boss)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metadata.score").value("1.5"));
        String henryToken = loginOrg(orgAuth, orgId, "henry", "orgpass1");
        mockMvc.perform(get(org + "/users/me").header("Authorization", bearer(henryToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metadata['employee-id']").value("EMP123"));

        // partial update: touched keys only; null removes a key
        java.util.Map<String, Object> partialMetadata = new java.util.HashMap<>();
        partialMetadata.put("employee-id", "EMP456");
        partialMetadata.put("joined", null);
        mockMvc.perform(patch(org + "/users/" + henry)
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("metadata", partialMetadata))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metadata['employee-id']").value("EMP456"))
                .andExpect(jsonPath("$.metadata.joined").doesNotExist())
                .andExpect(jsonPath("$.metadata.active").value("true"));

        // unknown field keys and type mismatches are rejected
        mockMvc.perform(patch(org + "/users/" + henry)
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("metadata", Map.of("nope", "x")))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch(org + "/users/" + henry)
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("metadata", Map.of("score", "abc")))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch(org + "/users/" + henry)
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("metadata", Map.of("active", "maybe")))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch(org + "/users/" + henry)
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("metadata", Map.of("joined", "05-01-2026")))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void loginByLoginEnabledFieldsWithNormalizedValuesAndUniqueness() throws Exception {
        String platform = "/" + SLUGS[1];
        String orgAuth = platform + "/auth";
        String boss = registerPlatform("ouf-login@nexx.io", SLUGS[1]);
        long orgId = createOrganisation(boss, platform);
        String org = platform + "/organisations/" + orgId;

        createField(boss, org, "employee-id", "STRING", true);
        createField(boss, org, "score", "NUMBER", true);
        createField(boss, org, "joined", "DATE", true);
        createField(boss, org, "active", "BOOLEAN", false);

        createUserWithPassword(boss, org, "alice", Map.of(
                "employee-id", "EMP123",
                "score", "1.50",
                "joined", "2026-01-05",
                "active", "true"));

        // login by each login-enabled field, values normalized on the way in
        mockMvc.perform(post(orgAuth + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("organisationId", orgId, "identifier", "EMP123", "password", "orgpass1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.metadata['employee-id']").value("EMP123"));
        mockMvc.perform(post(orgAuth + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("organisationId", orgId, "identifier", "1.5", "password", "orgpass1"))))
                .andExpect(status().isOk());
        mockMvc.perform(post(orgAuth + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("organisationId", orgId, "identifier", "1.50", "password", "orgpass1"))))
                .andExpect(status().isOk());
        mockMvc.perform(post(orgAuth + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("organisationId", orgId, "identifier", "2026-01-05", "password", "orgpass1"))))
                .andExpect(status().isOk());

        // STRING login values match case-insensitively
        mockMvc.perform(post(orgAuth + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("organisationId", orgId, "identifier", "emp123", "password", "orgpass1"))))
                .andExpect(status().isOk());

        // ...so case-differing values count as duplicates on a login field
        mockMvc.perform(post(org + "/users")
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("firstName", "B", "lastName", "Case",
                                "password", "orgpass1",
                                "metadata", Map.of("employee-id", "emp123")))))
                .andExpect(status().isConflict());

        // a non-login-enabled field's value is not a login identifier
        mockMvc.perform(post(orgAuth + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("organisationId", orgId, "identifier", "true", "password", "orgpass1"))))
                .andExpect(status().isUnauthorized());

        // login-enabled values must stay unique (raw and normalized duplicates)
        mockMvc.perform(post(org + "/users")
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("firstName", "B", "lastName", "Two",
                                "password", "orgpass1",
                                "metadata", Map.of("employee-id", "EMP123")))))
                .andExpect(status().isConflict());
        mockMvc.perform(post(org + "/users")
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("firstName", "B", "lastName", "Three",
                                "password", "orgpass1",
                                "metadata", Map.of("score", "1.50")))))
                .andExpect(status().isConflict());

        // duplicates are fine on a non-login field (active)
        mockMvc.perform(post(org + "/users")
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("firstName", "B", "lastName", "Four",
                                "password", "orgpass1",
                                "metadata", Map.of("active", "true")))))
                .andExpect(status().isCreated());
    }

    @Test
    void fieldManagementIsGatedByOrgRolePermissions() throws Exception {
        String platform = "/" + SLUGS[2];
        String orgAuth = platform + "/auth";
        String boss = registerPlatform("ouf-rbac@nexx.io", SLUGS[2]);
        long orgId = createOrganisation(boss, platform);
        String org = platform + "/organisations/" + orgId;

        long adminRole = createRole(boss, org, "FieldAdmin",
                "ORGANISATION_USER_FIELD_READ",
                "ORGANISATION_USER_FIELD_CREATE",
                "ORGANISATION_USER_FIELD_UPDATE",
                "ORGANISATION_USER_FIELD_DELETE");
        long viewerRole = createRole(boss, org, "FieldViewer", "ORGANISATION_USER_FIELD_READ");

        long ada = registerOrgUser(orgAuth, orgId, "ada", "orgpass1");
        long vick = registerOrgUser(orgAuth, orgId, "vick", "orgpass1");
        long noel = registerOrgUser(orgAuth, orgId, "noel", "orgpass1");
        patchOrgUser(boss, org, ada, Map.of("roleIds", List.of(adminRole)));
        patchOrgUser(boss, org, vick, Map.of("roleIds", List.of(viewerRole)));

        String adaToken = loginOrg(orgAuth, orgId, "ada", "orgpass1");
        String vickToken = loginOrg(orgAuth, orgId, "vick", "orgpass1");
        String noelToken = loginOrg(orgAuth, orgId, "noel", "orgpass1");

        // no role at all: no read
        mockMvc.perform(get(org + "/user-fields").header("Authorization", bearer(noelToken)))
                .andExpect(status().isForbidden());

        // viewer: can read, cannot create/update/delete
        mockMvc.perform(get(org + "/user-fields").header("Authorization", bearer(vickToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(post(org + "/user-fields")
                        .header("Authorization", bearer(vickToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("key", "nickname", "fieldType", "STRING"))))
                .andExpect(status().isForbidden());

        // admin: full CRUD
        mockMvc.perform(post(org + "/user-fields")
                        .header("Authorization", bearer(adaToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("key", "nickname", "fieldType", "STRING", "loginEnabled", true))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.key").value("nickname"))
                .andExpect(jsonPath("$.loginEnabled").value(true));
        mockMvc.perform(patch(org + "/user-fields/1")
                        .header("Authorization", bearer(vickToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("loginEnabled", false))))
                .andExpect(status().isForbidden());

        long fieldId = getId(mockMvc.perform(get(org + "/user-fields")
                        .header("Authorization", bearer(adaToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].key").value("nickname"))
                .andReturn(), "/0/id");

        mockMvc.perform(patch(org + "/user-fields/" + fieldId)
                        .header("Authorization", bearer(adaToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("loginEnabled", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loginEnabled").value(false));
        mockMvc.perform(delete(org + "/user-fields/" + fieldId)
                        .header("Authorization", bearer(vickToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete(org + "/user-fields/" + fieldId)
                        .header("Authorization", bearer(adaToken)))
                .andExpect(status().isNoContent());
    }

    @Test
    void registerAcceptsValidatedMetadata() throws Exception {
        String platform = "/" + SLUGS[3];
        String orgAuth = platform + "/auth";
        String boss = registerPlatform("ouf-reg@nexx.io", SLUGS[3]);
        long orgId = createOrganisation(boss, platform);
        String org = platform + "/organisations/" + orgId;

        createField(boss, org, "badge", "STRING", false);

        mockMvc.perform(post(orgAuth + "/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "organisationId", orgId,
                                "username", "newbie",
                                "password", "orgpass1",
                                "firstName", "N", "lastName", "B",
                                "metadata", Map.of("badge", "B123")))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.metadata.badge").value("B123"));

        // unknown keys are rejected at registration too
        mockMvc.perform(post(orgAuth + "/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "organisationId", orgId,
                                "username", "other",
                                "password", "orgpass1",
                                "firstName", "O", "lastName", "T",
                                "metadata", Map.of("mystery", "x")))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void typeChangeBlockedAndLoginEnableRejectsDuplicateValues() throws Exception {
        String platform = "/" + SLUGS[4];
        String orgAuth = platform + "/auth";
        String boss = registerPlatform("ouf-guard@nexx.io", SLUGS[4]);
        long orgId = createOrganisation(boss, platform);
        String org = platform + "/organisations/" + orgId;

        long tag = getId(mockMvc.perform(post(org + "/user-fields")
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("key", "tag", "fieldType", "STRING"))))
                .andExpect(status().isCreated())
                .andReturn(), "/id");

        createUserWithPassword(boss, org, "u1", Map.of("tag", "shared"));
        createUserWithPassword(boss, org, "u2", Map.of("tag", "shared"));

        // duplicates were fine while login was off (both users created)...

        // ...so enabling login now must be rejected
        mockMvc.perform(patch(org + "/user-fields/" + tag)
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("loginEnabled", true))))
                .andExpect(status().isConflict());

        // the type cannot change while values exist
        mockMvc.perform(patch(org + "/user-fields/" + tag)
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fieldType", "NUMBER"))))
                .andExpect(status().isBadRequest());

        // a fresh field (no values) can change type
        long empty = getId(mockMvc.perform(post(org + "/user-fields")
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("key", "count", "fieldType", "STRING"))))
                .andExpect(status().isCreated())
                .andReturn(), "/id");
        mockMvc.perform(patch(org + "/user-fields/" + empty)
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fieldType", "NUMBER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fieldType").value("NUMBER"));
    }

    @Test
    void deletingFieldRemovesValuesAndBreaksLogin() throws Exception {
        String platform = "/" + SLUGS[5];
        String orgAuth = platform + "/auth";
        String boss = registerPlatform("ouf-del@nexx.io", SLUGS[5]);
        long orgId = createOrganisation(boss, platform);
        String org = platform + "/organisations/" + orgId;

        long fieldId = createField(boss, org, "emp", "STRING", true);
        long carol = createUserWithPassword(boss, org, "carol", Map.of("emp", "E1"));

        // login works through the field
        mockMvc.perform(post(orgAuth + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("organisationId", orgId, "identifier", "E1", "password", "orgpass1"))))
                .andExpect(status().isOk());

        // deleting the field removes its values
        mockMvc.perform(delete(org + "/user-fields/" + fieldId)
                        .header("Authorization", bearer(boss)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(org + "/users/" + carol).header("Authorization", bearer(boss)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metadata.emp").doesNotExist());

        // the field is gone and can no longer be used to log in
        mockMvc.perform(get(org + "/user-fields").header("Authorization", bearer(boss)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(post(orgAuth + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("organisationId", orgId, "identifier", "E1", "password", "orgpass1"))))
                .andExpect(status().isUnauthorized());
    }

    // --- helpers ---

    private String registerPlatform(String email, String slug) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "firstName", "F", "lastName", "L",
                                "email", email, "password", "password1",
                                "platformName", "User Field Platform", "platformSlug", slug))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private long createOrganisation(String boss, String platform) throws Exception {
        MvcResult result = mockMvc.perform(post(platform + "/organisations")
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "OUF Org", "slug", ORG_SLUG))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private long createField(String boss, String org, String key, String type, boolean loginEnabled)
            throws Exception {
        return getId(mockMvc.perform(post(org + "/user-fields")
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("key", key, "fieldType", type,
                                "loginEnabled", loginEnabled))))
                .andExpect(status().isCreated())
                .andReturn(), "/id");
    }

    private long createRole(String boss, String org, String name, String... permissions) throws Exception {
        return getId(mockMvc.perform(post(org + "/roles")
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", name, "permissions", List.of(permissions)))))
                .andExpect(status().isCreated())
                .andReturn(), "/id");
    }

    private long registerOrgUser(String orgAuth, long orgId, String identifier, String password) throws Exception {
        return getId(mockMvc.perform(post(orgAuth + "/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "organisationId", orgId,
                                "username", identifier,
                                "password", password,
                                "firstName", "F", "lastName", "L"))))
                .andExpect(status().isCreated())
                .andReturn(), "/user/id");
    }

    private long createUserWithPassword(String boss, String org, String username, Map<String, String> metadata)
            throws Exception {
        return getId(mockMvc.perform(post(org + "/users")
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "firstName", "F", "lastName", "L",
                                "username", username,
                                "password", "orgpass1",
                                "metadata", metadata))))
                .andExpect(status().isCreated())
                .andReturn(), "/id");
    }

    private void patchOrgUser(String boss, String org, long userId, Map<String, Object> body) throws Exception {
        mockMvc.perform(patch(org + "/users/" + userId)
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk());
    }

    private String loginOrg(String orgAuth, long orgId, String identifier, String password) throws Exception {
        MvcResult result = mockMvc.perform(post(orgAuth + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("organisationId", orgId, "identifier", identifier, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private long getId(MvcResult result, String path) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).at(path).asLong();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
