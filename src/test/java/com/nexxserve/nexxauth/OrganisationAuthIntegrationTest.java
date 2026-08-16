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

import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Organisation-level auth: org users register/login under the platform slug,
 * get tokens signed by the organisation's own RSA key (roles in claims,
 * permissions resolved server-side), and are gated by their role permissions on
 * org endpoints. Platform auth flow stays untouched.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OrganisationAuthIntegrationTest {

    // Each test registers its own platform (unique slug) because all tests
    // share one database; a shared slug would 409 on the second registration.
    private static final String[] SLUGS = {"orgauth1", "orgauth2", "orgauth3", "orgauth4", "orgauth5", "orgauth6", "orgauth7", "orgauth8", "orgauth9", "orgauth10"};
    private static final String ORG_SLUG = "oa-org";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void orgUserCanRegisterLoginReadSelfAndBePermissionGated() throws Exception {
        String platform = "/" + SLUGS[0];
        String org = platform + "/organisations/" + ORG_SLUG;
        String orgAuth = platform + "/auth";
        String boss = registerPlatform("orgauth-boss@nexx.io", SLUGS[0]);
        createOrganisation(boss, platform);
        long orgId = getOrgId(boss, org);

        // register an org user (public endpoint, no platform token needed)
        MvcResult reg = mockMvc.perform(post(orgAuth + "/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "organisationId", orgId,
                                "identifier", "jane",
                                "password", "orgpass1",
                                "firstName", "Jane",
                                "lastName", "Doe"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.username").value("jane"))
                .andExpect(jsonPath("$.user.roles.length()").value(0))
                .andReturn();
        String token = objectMapper.readTree(reg.getResponse().getContentAsString()).get("accessToken").asText();

        // org token claims carry the org id and empty roles/permissions
        JsonNode claims = decodeClaims(token);
        assertEquals(orgId, claims.get("orgId").asLong());
        assertEquals("oa-org", claims.get("orgSlug").asText());
        assertEquals("org-access", claims.get("type").asText());

        // login with the same identifier + password
        mockMvc.perform(post(orgAuth + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("organisationId", orgId, "identifier", "jane", "password", "orgpass1"))))
                .andExpect(status().isOk());
        mockMvc.perform(post(orgAuth + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("organisationId", orgId, "identifier", "jane", "password", "wrong"))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(orgAuth + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("organisationId", 999999L, "identifier", "jane", "password", "orgpass1"))))
                .andExpect(status().isNotFound());

        // every org user can read their own profile regardless of permissions
        mockMvc.perform(get(org + "/users/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("jane"));

        // without a role, the org user cannot list users or create them
        mockMvc.perform(get(org + "/users").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(org + "/users")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("firstName", "A", "lastName", "B"))))
                .andExpect(status().isForbidden());

        // duplicate identifier on register conflicts
        mockMvc.perform(post(orgAuth + "/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "organisationId", orgId,
                                "identifier", "jane",
                                "password", "orgpass1",
                                "firstName", "J", "lastName", "D"))))
                .andExpect(status().isConflict());
    }

    @Test
    void orgRolesGateOrgEndpointsForOrgUsers() throws Exception {
        String platform = "/" + SLUGS[1];
        String org = platform + "/organisations/" + ORG_SLUG;
        String orgAuth = platform + "/auth";
        String boss = registerPlatform("orgauth-role-boss@nexx.io", SLUGS[1]);
        createOrganisation(boss, platform);
        long orgId = getOrgId(boss, org);

        // boss creates a role with READ + CREATE and one with no permissions
        long adminRole = createRole(boss, org, "Admin",
                "ORGANISATION_USER_READ", "ORGANISATION_USER_CREATE");
        long viewerRole = createRole(boss, org, "Viewer");

        // register two org users
        long jane = registerOrgUser(orgAuth, orgId, "jane", "orgpass1");
        long mark = registerOrgUser(orgAuth, orgId, "mark", "orgpass1");

        // boss assigns roles (platform super user path, unchanged)
        patchOrgUser(boss, org, jane, Map.of("roleIds", List.of(adminRole)));
        patchOrgUser(boss, org, mark, Map.of("roleIds", List.of(viewerRole)));

        String janeToken = loginOrg(orgAuth, orgId, "jane", "orgpass1");
        String markToken = loginOrg(orgAuth, orgId, "mark", "orgpass1");

        // claims carry roles only - permissions are internal, never in the token
        JsonNode janeClaims = decodeClaims(janeToken);
        assertEquals(1, janeClaims.get("roles").size());
        assertEquals("Admin", janeClaims.get("roles").get(0).asText());
        assertFalse(janeClaims.has("permissions"));

        // Jane (READ+CREATE) can list and create; Mark (no permissions) cannot
        mockMvc.perform(get(org + "/users").header("Authorization", bearer(janeToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(post(org + "/users")
                        .header("Authorization", bearer(janeToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("firstName", "New", "lastName", "Guy"))))
                .andExpect(status().isCreated());
        mockMvc.perform(get(org + "/users").header("Authorization", bearer(markToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(org + "/users")
                        .header("Authorization", bearer(markToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("firstName", "A", "lastName", "B"))))
                .andExpect(status().isForbidden());

        // role list requires the read permission too
        mockMvc.perform(get(org + "/roles").header("Authorization", bearer(janeToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(get(org + "/roles").header("Authorization", bearer(markToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void orgUserPermissionsGateUpdatesAndDeletes() throws Exception {
        String platform = "/" + SLUGS[6];
        String org = platform + "/organisations/" + ORG_SLUG;
        String orgAuth = platform + "/auth";
        String boss = registerPlatform("orgauth-crud-boss@nexx.io", SLUGS[6]);
        createOrganisation(boss, platform);
        long orgId = getOrgId(boss, org);

        // one role with UPDATE+DELETE, one with only READ
        long adminRole = createRole(boss, org, "Admins",
                "ORGANISATION_USER_UPDATE", "ORGANISATION_USER_DELETE");
        long viewerRole = createRole(boss, org, "Viewers", "ORGANISATION_USER_READ");

        long admin = registerOrgUser(orgAuth, orgId, "carol", "orgpass1");
        long viewer = registerOrgUser(orgAuth, orgId, "dave", "orgpass1");
        long target = registerOrgUser(orgAuth, orgId, "erin", "orgpass1");
        patchOrgUser(boss, org, admin, Map.of("roleIds", List.of(adminRole)));
        patchOrgUser(boss, org, viewer, Map.of("roleIds", List.of(viewerRole)));

        String adminToken = loginOrg(orgAuth, orgId, "carol", "orgpass1");
        String viewerToken = loginOrg(orgAuth, orgId, "dave", "orgpass1");

        // UPDATE permission allows patching others; READ-only is rejected
        mockMvc.perform(patch(org + "/users/" + target)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("firstName", "Erin Renamed"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Erin Renamed"));
        mockMvc.perform(patch(org + "/users/" + target)
                        .header("Authorization", bearer(viewerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("firstName", "Nope"))))
                .andExpect(status().isForbidden());

        // DELETE permission allows deleting; READ-only is rejected
        mockMvc.perform(delete(org + "/users/" + target)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete(org + "/users/" + viewer)
                        .header("Authorization", bearer(viewerToken)))
                .andExpect(status().isForbidden());

        // the deleted user is gone; the admin still exists
        mockMvc.perform(get(org + "/users/" + target).header("Authorization", bearer(boss)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(org + "/users/" + admin).header("Authorization", bearer(boss)))
                .andExpect(status().isOk());
    }

    @Test
    void orgUserCanReadOwnOrganisationButNotThePlatformDirectory() throws Exception {
        String platform = "/" + SLUGS[7];
        String org = platform + "/organisations/" + ORG_SLUG;
        String orgAuth = platform + "/auth";
        String boss = registerPlatform("orgauth-orgread-boss@nexx.io", SLUGS[7]);
        createOrganisation(boss, platform);
        long orgId = getOrgId(boss, org);
        registerOrgUser(orgAuth, orgId, "frank", "orgpass1");
        String frankToken = loginOrg(orgAuth, orgId, "frank", "orgpass1");

        // every org user reads their own org context by default (no permission)
        mockMvc.perform(get(org).header("Authorization", bearer(frankToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value(ORG_SLUG));

        // but the platform-level org directory is off-limits to org users
        mockMvc.perform(get(platform + "/organisations").header("Authorization", bearer(frankToken)))
                .andExpect(status().isForbidden());

        // and a second organisation's context is off-limits too (cross-org)
        mockMvc.perform(post(platform + "/organisations")
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Other", "slug", "oa-other"))))
                .andExpect(status().isCreated());
        mockMvc.perform(get(platform + "/organisations/oa-other").header("Authorization", bearer(frankToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void orgTokensAreScopedToTheirOrganisationAndDoNotTouchPlatform() throws Exception {
        String platform = "/" + SLUGS[2];
        String org = platform + "/organisations/" + ORG_SLUG;
        String orgAuth = platform + "/auth";
        String boss = registerPlatform("orgauth-iso-boss@nexx.io", SLUGS[2]);
        createOrganisation(boss, platform);
        long orgId = getOrgId(boss, org);
        registerOrgUser(orgAuth, orgId, "alice", "orgpass1");
        String aliceToken = loginOrg(orgAuth, orgId, "alice", "orgpass1");

        // a second organisation in the same platform
        mockMvc.perform(post(platform + "/organisations")
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Other Org", "slug", "oa-other"))))
                .andExpect(status().isCreated());

        // alice's token is rejected on the other organisation
        mockMvc.perform(get(platform + "/organisations/oa-other/users/me")
                        .header("Authorization", bearer(aliceToken)))
                .andExpect(status().isForbidden());

        // org tokens never grant platform access
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(aliceToken)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(platform).header("Authorization", bearer(aliceToken)))
                .andExpect(status().isUnauthorized());

        // platform tokens still work on org endpoints (current behaviour)
        mockMvc.perform(get(org + "/users").header("Authorization", bearer(boss)))
                .andExpect(status().isOk());
    }

    @Test
    void orgKeysArePublicRotatableAndOldTokensKeepVerifying() throws Exception {
        String platform = "/" + SLUGS[3];
        String org = platform + "/organisations/" + ORG_SLUG;
        String orgAuth = platform + "/auth";
        String boss = registerPlatform("orgauth-key-boss@nexx.io", SLUGS[3]);
        createOrganisation(boss, platform);
        long orgId = getOrgId(boss, org);
        registerOrgUser(orgAuth, orgId, "karen", "orgpass1");
        String karenToken = loginOrg(orgAuth, orgId, "karen", "orgpass1");

        // public keys endpoint (no auth)
        MvcResult keys = mockMvc.perform(get(org + "/keys"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].active").value(true))
                .andReturn();
        JsonNode keyNode = objectMapper.readTree(keys.getResponse().getContentAsString()).get(0);
        String kid = keyNode.get("kid").asText();
        assertEquals(kid, decodeHeader(karenToken).get("kid").asText());

        // the public key is a real RSA public key (starts with X.509 base64)
        byte[] der = Base64.getDecoder().decode(keyNode.get("publicKey").asText());
        java.security.KeyFactory factory = java.security.KeyFactory.getInstance("RSA");
        java.security.PublicKey publicKey = factory.generatePublic(new java.security.spec.X509EncodedKeySpec(der));
        assertNotNull(publicKey);

        // rotate (platform super user); a new kid becomes active
        MvcResult rotated = mockMvc.perform(post(org + "/keys/rotate")
                        .header("Authorization", bearer(boss)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andReturn();
        String newKid = objectMapper.readTree(rotated.getResponse().getContentAsString()).get("kid").asText();
        assertNotEquals(kid, newKid);

        // both keys are listed; the old token still verifies against the retired key
        mockMvc.perform(get(org + "/keys"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(get(org + "/users/me").header("Authorization", bearer(karenToken)))
                .andExpect(status().isOk());

        // non-super platform members cannot rotate
        String memberToken = addReadOnlyMember(boss, platform);
        mockMvc.perform(post(org + "/keys/rotate")
                        .header("Authorization", bearer(memberToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void orgRefreshRotatesAndReuseRevokesFamily() throws Exception {
        String platform = "/" + SLUGS[4];
        String orgAuth = platform + "/auth";
        String boss = registerPlatform("orgauth-refresh-boss@nexx.io", SLUGS[4]);
        createOrganisation(boss, platform);
        long orgId = getOrgId(boss, platform + "/organisations/" + ORG_SLUG);
        registerOrgUser(orgAuth, orgId, "nora", "orgpass1");

        MvcResult login = mockMvc.perform(post(orgAuth + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("organisationId", orgId, "identifier", "nora", "password", "orgpass1"))))
                .andExpect(status().isOk())
                .andReturn();
        String refresh1 = objectMapper.readTree(login.getResponse().getContentAsString()).get("refreshToken").asText();

        // refresh rotates: the new token works, the old one is dead
        MvcResult rotated = mockMvc.perform(post(orgAuth + "/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", refresh1))))
                .andExpect(status().isOk())
                .andReturn();
        String refresh2 = objectMapper.readTree(rotated.getResponse().getContentAsString()).get("refreshToken").asText();

        // replaying the rotated token revokes the whole family
        mockMvc.perform(post(orgAuth + "/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", refresh1))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(orgAuth + "/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", refresh2))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void usernamesAreCaseInsensitive() throws Exception {
        String platform = "/" + SLUGS[8];
        String org = platform + "/organisations/" + ORG_SLUG;
        String orgAuth = platform + "/auth";
        String boss = registerPlatform("orgauth-case-boss@nexx.io", SLUGS[8]);
        createOrganisation(boss, platform);
        long orgId = getOrgId(boss, org);

        // registering "MixedCase" stores it lowercase, so Bob/bob are one account
        MvcResult reg = mockMvc.perform(post(orgAuth + "/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "organisationId", orgId,
                                "identifier", "MixedCase",
                                "password", "orgpass1",
                                "firstName", "F", "lastName", "L"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.username").value("mixedcase"))
                .andReturn();
        assertEquals("mixedcase", objectMapper.readTree(reg.getResponse().getContentAsString())
                .get("user").get("username").asText());

        // login matches case-insensitively in both directions
        mockMvc.perform(post(orgAuth + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("organisationId", orgId, "identifier", "MIXEDCASE", "password", "orgpass1"))))
                .andExpect(status().isOk());
        mockMvc.perform(post(orgAuth + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("organisationId", orgId, "identifier", "mixedcase", "password", "orgpass1"))))
                .andExpect(status().isOk());

        // a differently-cased spelling of the same name cannot be registered twice
        mockMvc.perform(post(orgAuth + "/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "organisationId", orgId,
                                "identifier", "MIXEDCASE",
                                "password", "orgpass1",
                                "firstName", "G", "lastName", "R"))))
                .andExpect(status().isConflict());
    }

    @Test
    void adminPasswordResetRevokesExistingSessions() throws Exception {
        String platform = "/" + SLUGS[9];
        String org = platform + "/organisations/" + ORG_SLUG;
        String orgAuth = platform + "/auth";
        String boss = registerPlatform("orgauth-reset-boss@nexx.io", SLUGS[9]);
        createOrganisation(boss, platform);
        long orgId = getOrgId(boss, org);
        long pam = registerOrgUser(orgAuth, orgId, "pam", "orgpass1");

        MvcResult login = mockMvc.perform(post(orgAuth + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("organisationId", orgId, "identifier", "pam", "password", "orgpass1"))))
                .andExpect(status().isOk())
                .andReturn();
        String oldRefresh = objectMapper.readTree(login.getResponse().getContentAsString())
                .get("refreshToken").asText();

        // admin resets pam's password (a live session exists)
        mockMvc.perform(patch(org + "/users/" + pam)
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("password", "newpass123"))))
                .andExpect(status().isOk());

        // the reset forces re-authentication: the old refresh token is dead
        mockMvc.perform(post(orgAuth + "/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", oldRefresh))))
                .andExpect(status().isUnauthorized());

        // the old password no longer works; the new one does
        mockMvc.perform(post(orgAuth + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("organisationId", orgId, "identifier", "pam", "password", "orgpass1"))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(orgAuth + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("organisationId", orgId, "identifier", "pam", "password", "newpass123"))))
                .andExpect(status().isOk());
    }

    @Test
    void useEmailAsUsernameSettingMakesEmailTheLoginIdentifier() throws Exception {
        String platform = "/" + SLUGS[5];
        String org = platform + "/organisations/" + ORG_SLUG;
        String orgAuth = platform + "/auth";
        String boss = registerPlatform("orgauth-email-boss@nexx.io", SLUGS[5]);
        createOrganisation(boss, platform);
        long orgId = getOrgId(boss, org);

        // enable the setting
        mockMvc.perform(patch(org)
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("useEmailAsUsername", true))))
                .andExpect(status().isOk());

        // register with the email as identifier
        mockMvc.perform(post(orgAuth + "/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "organisationId", orgId,
                                "identifier", "email-user@nexx.io",
                                "password", "orgpass1",
                                "firstName", "E", "lastName", "U"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.email").value("email-user@nexx.io"));

        // login with the email
        mockMvc.perform(post(orgAuth + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("organisationId", orgId,
                                "identifier", "EMAIL-USER@nexx.io", "password", "orgpass1"))))
                .andExpect(status().isOk());
    }

    // --- helpers ---

    private String registerPlatform(String email, String slug) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "firstName", "F", "lastName", "L",
                                "email", email, "password", "password1",
                                "platformName", "Org Auth Platform", "platformSlug", slug))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private void createOrganisation(String boss, String platform) throws Exception {
        mockMvc.perform(post(platform + "/organisations")
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "OA Org", "slug", ORG_SLUG))))
                .andExpect(status().isCreated());
    }

    private long getOrgId(String boss, String org) throws Exception {
        MvcResult result = mockMvc.perform(get(org)
                        .header("Authorization", bearer(boss)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private long createRole(String boss, String org, String name, String... permissions) throws Exception {
        MvcResult result = mockMvc.perform(post(org + "/roles")
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", name, "permissions", List.of(permissions)))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private long registerOrgUser(String orgAuth, long orgId, String identifier, String password) throws Exception {
        MvcResult result = mockMvc.perform(post(orgAuth + "/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "organisationId", orgId,
                                "identifier", identifier,
                                "password", password,
                                "firstName", "F", "lastName", "L"))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("user").get("id").asLong();
    }

    private String loginOrg(String orgAuth, long orgId, String identifier, String password) throws Exception {
        MvcResult result = mockMvc.perform(post(orgAuth + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("organisationId", orgId, "identifier", identifier, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private void patchOrgUser(String boss, String org, long userId, Map<String, Object> body) throws Exception {
        mockMvc.perform(patch(org + "/users/" + userId)
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk());
    }

    private String addReadOnlyMember(String boss, String platform) throws Exception {
        MvcResult add = mockMvc.perform(post(platform + "/users")
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "firstName", "R", "lastName", "O",
                                "email", "orgauth-ro@nexx.io", "password", "readonly-pw"))))
                .andExpect(status().isCreated())
                .andReturn();
        long memberId = objectMapper.readTree(add.getResponse().getContentAsString()).get("id").asLong();
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "orgauth-ro@nexx.io", "password", "readonly-pw"))))
                .andExpect(status().isOk());
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "orgauth-ro@nexx.io", "password", "readonly-pw"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(login.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private JsonNode decodeClaims(String token) throws Exception {
        String payload = token.split("\\.")[1];
        byte[] decoded = Base64.getUrlDecoder().decode(payload);
        return objectMapper.readTree(new String(decoded, java.nio.charset.StandardCharsets.UTF_8));
    }

    private JsonNode decodeHeader(String token) throws Exception {
        String header = token.split("\\.")[0];
        byte[] decoded = Base64.getUrlDecoder().decode(header);
        return objectMapper.readTree(new String(decoded, java.nio.charset.StandardCharsets.UTF_8));
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
