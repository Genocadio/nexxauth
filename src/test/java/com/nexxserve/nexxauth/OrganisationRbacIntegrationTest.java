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
class OrganisationRbacIntegrationTest {

    // Each test registers its own platform (unique name -> unique slug) because
    // all tests share one database; a shared platform name would slug-dedup the
    // later tests (rbac-platform-2, ...) and break their org paths.
    private static final String ROLE_PLATFORM = "/rbac-roles/organisations/rbac-org";
    private static final String USER_PLATFORM = "/rbac-users/organisations/rbac-org";
    private static final String EMAIL_PLATFORM = "/rbac-email-setting/organisations/rbac-org";
    private static final String SCOPE_PLATFORM = "/rbac-scope/organisations/rbac-org";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void roleLifecycleWithPermissions() throws Exception {
        String boss = register("rbac-boss@nexx.io", "RBAC Roles Platform", "rbac-roles");
        createOrganisation(boss, ROLE_PLATFORM);

        // create a role with permissions; default is off unless asked
        MvcResult created = mockMvc.perform(post(ROLE_PLATFORM + "/roles")
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Admin",
                                "permissions", java.util.List.of("ORGANISATION_USER_READ", "ORGANISATION_USER_CREATE")))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Admin"))
                .andExpect(jsonPath("$.permissions.length()").value(2))
                .andExpect(jsonPath("$.isDefault").value(false))
                .andReturn();
        long adminRoleId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        // a role created as default reports isDefault=true and can be toggled off
        MvcResult defaultRole = mockMvc.perform(post(ROLE_PLATFORM + "/roles")
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Defaulted",
                                "permissions", java.util.List.of("ORGANISATION_USER_READ"),
                                "isDefault", true))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isDefault").value(true))
                .andReturn();
        long defaultRoleId = objectMapper.readTree(defaultRole.getResponse().getContentAsString()).get("id").asLong();
        mockMvc.perform(patch(ROLE_PLATFORM + "/roles/" + defaultRoleId)
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("isDefault", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isDefault").value(false));

        // a role with no permissions is valid
        MvcResult viewer = mockMvc.perform(post(ROLE_PLATFORM + "/roles")
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Viewer"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.permissions.length()").value(0))
                .andReturn();
        long viewerRoleId = objectMapper.readTree(viewer.getResponse().getContentAsString()).get("id").asLong();

        // list + get
        mockMvc.perform(get(ROLE_PLATFORM + "/roles").header("Authorization", bearer(boss)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
        mockMvc.perform(get(ROLE_PLATFORM + "/roles/" + viewerRoleId).header("Authorization", bearer(boss)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Viewer"));

        // update: rename + replace permissions
        mockMvc.perform(patch(ROLE_PLATFORM + "/roles/" + viewerRoleId)
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Reader",
                                "permissions", java.util.List.of("ORGANISATION_USER_READ")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Reader"))
                .andExpect(jsonPath("$.permissions.length()").value(1));

        // duplicate role name conflicts
        mockMvc.perform(post(ROLE_PLATFORM + "/roles")
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Admin"))))
                .andExpect(status().isConflict());

        // delete
        mockMvc.perform(delete(ROLE_PLATFORM + "/roles/" + adminRoleId).header("Authorization", bearer(boss)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(ROLE_PLATFORM + "/roles/" + adminRoleId).header("Authorization", bearer(boss)))
                .andExpect(status().isNotFound());
    }

    @Test
    void organisationUserLifecycleWithRoles() throws Exception {
        String boss = register("rbac-user-boss@nexx.io", "RBAC Users Platform", "rbac-users");
        createOrganisation(boss, USER_PLATFORM);

        long adminRoleId = createRole(boss, USER_PLATFORM, "Admin", "ORGANISATION_USER_READ", "ORGANISATION_USER_UPDATE");

        // create with username + email + roles
        MvcResult created = mockMvc.perform(post(USER_PLATFORM + "/users")
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "firstName", "Jane",
                                "lastName", "Doe",
                                "username", "jane",
                                "email", "jane@acme.io",
                                "roleIds", java.util.List.of(adminRoleId)))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("jane"))
                .andExpect(jsonPath("$.email").value("jane@acme.io"))
                .andExpect(jsonPath("$.roles.length()").value(1))
                .andExpect(jsonPath("$.roles[0].name").value("Admin"))
                // user responses carry roles only - never permissions
                .andExpect(jsonPath("$.roles[0].permissions").doesNotExist())
                .andReturn();
        long userId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        // identifiers are unique per organisation
        mockMvc.perform(post(USER_PLATFORM + "/users")
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("firstName", "J", "lastName", "R", "email", "jane@acme.io"))))
                .andExpect(status().isConflict());
        mockMvc.perform(post(USER_PLATFORM + "/users")
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("firstName", "J", "lastName", "R", "username", "jane"))))
                .andExpect(status().isConflict());

        // both identifiers optional when useEmailAsUsername is off
        mockMvc.perform(post(USER_PLATFORM + "/users")
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("firstName", "No", "lastName", "Id"))))
                .andExpect(status().isCreated());

        // update: rename, swap roles, disable, clear email with ""
        mockMvc.perform(patch(USER_PLATFORM + "/users/" + userId)
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "firstName", "Janet",
                                "email", "",
                                "roleIds", java.util.List.of(),
                                "enabled", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Janet"))
                .andExpect(jsonPath("$.email").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.roles.length()").value(0))
                .andExpect(jsonPath("$.enabled").value(false));

        // delete
        mockMvc.perform(delete(USER_PLATFORM + "/users/" + userId).header("Authorization", bearer(boss)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(USER_PLATFORM + "/users/" + userId).header("Authorization", bearer(boss)))
                .andExpect(status().isNotFound());
    }

    @Test
    void useEmailAsUsernameSettingRequiresEmail() throws Exception {
        String boss = register("rbac-email-boss@nexx.io", "RBAC Email Platform", "rbac-email-setting");
        createOrganisation(boss, EMAIL_PLATFORM);

        // without the setting, a user without email is fine
        mockMvc.perform(post(EMAIL_PLATFORM + "/users")
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("firstName", "A", "lastName", "B"))))
                .andExpect(status().isCreated());

        // enable the setting
        mockMvc.perform(patch(EMAIL_PLATFORM)
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("useEmailAsUsername", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.useEmailAsUsername").value(true));

        // now email is required
        mockMvc.perform(post(EMAIL_PLATFORM + "/users")
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("firstName", "C", "lastName", "D"))))
                .andExpect(status().isBadRequest());

        // email is unique per org
        MvcResult withEmail = mockMvc.perform(post(EMAIL_PLATFORM + "/users")
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("firstName", "E", "lastName", "F", "email", "e@acme.io"))))
                .andExpect(status().isCreated())
                .andReturn();
        long userId = objectMapper.readTree(withEmail.getResponse().getContentAsString()).get("id").asLong();
        mockMvc.perform(post(EMAIL_PLATFORM + "/users")
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("firstName", "G", "lastName", "H", "email", "e@acme.io"))))
                .andExpect(status().isConflict());

        // clearing the email while the setting is on is rejected
        mockMvc.perform(patch(EMAIL_PLATFORM + "/users/" + userId)
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", ""))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rolesAreOrganisationScopedAndReadOnlyMembersCannotWrite() throws Exception {
        String boss = register("rbac-scope-boss@nexx.io", "RBAC Scope Platform", "rbac-scope");
        createOrganisation(boss, SCOPE_PLATFORM);
        String boss2 = register("rbac-scope-boss2@nexx.io", "RBAC Other Platform", "rbac-other");
        createOrganisationIn(boss2, "rbac-other", "Other Org");

        long roleA = createRole(boss, SCOPE_PLATFORM, "Shared", "ORGANISATION_USER_READ");
        long roleB = createRole(boss2, "/rbac-other/organisations/rbac-other", "Shared", "ORGANISATION_USER_DELETE");

        // same role name in different organisations is fine
        mockMvc.perform(get(SCOPE_PLATFORM + "/roles").header("Authorization", bearer(boss)))
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/rbac-other/organisations/rbac-other/roles")
                        .header("Authorization", bearer(boss2)))
                .andExpect(jsonPath("$.length()").value(1));

        // assigning a role from another organisation is rejected
        mockMvc.perform(post(SCOPE_PLATFORM + "/users")
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "firstName", "A", "lastName", "B",
                                "roleIds", java.util.List.of(roleB)))))
                .andExpect(status().isBadRequest());

        // a member of the other platform cannot touch this org's roles/users
        mockMvc.perform(get(SCOPE_PLATFORM + "/roles").header("Authorization", bearer(boss2)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(SCOPE_PLATFORM + "/users").header("Authorization", bearer(boss2)))
                .andExpect(status().isForbidden());

        // read-only platform member can read but not write org data
        MvcResult add = mockMvc.perform(post("/rbac-scope/users")
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "firstName", "R", "lastName", "O",
                                "email", "ro-rbac@nexx.io", "password", "password1"))))
                .andExpect(status().isCreated())
                .andReturn();
        long memberId = objectMapper.readTree(add.getResponse().getContentAsString()).get("id").asLong();
        MvcResult login = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "ro-rbac@nexx.io", "password", "password1"))))
                .andExpect(status().isOk())
                .andReturn();
        String member = objectMapper.readTree(login.getResponse().getContentAsString()).get("accessToken").asText();

        mockMvc.perform(get(SCOPE_PLATFORM + "/roles").header("Authorization", bearer(member)))
                .andExpect(status().isOk());
        mockMvc.perform(post(SCOPE_PLATFORM + "/roles")
                        .header("Authorization", bearer(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Nope"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(SCOPE_PLATFORM + "/users")
                        .header("Authorization", bearer(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("firstName", "A", "lastName", "B"))))
                .andExpect(status().isForbidden());
    }

    // --- helpers ---

    private String register(String email, String platformName, String platformSlug) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "firstName", "F",
                                "lastName", "L",
                                "email", email,
                                "password", "password1",
                                "platformName", platformName,
                                "platformSlug", platformSlug))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private void createOrganisation(String boss, String orgPath) throws Exception {
        String platformSlug = orgPath.split("/")[1];
        mockMvc.perform(post("/" + platformSlug + "/organisations")
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "RBAC Org", "slug", "rbac-org"))))
                .andExpect(status().isCreated());
    }

    private void createOrganisationIn(String boss, String slug, String name) throws Exception {
        mockMvc.perform(post("/rbac-other/organisations")
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", name, "slug", slug))))
                .andExpect(status().isCreated());
    }

    private long createRole(String boss, String orgPath, String name, String... permissions) throws Exception {
        MvcResult result = mockMvc.perform(post(orgPath + "/roles")
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", name, "permissions", java.util.List.of(permissions)))))
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
