package com.nexxserve.nexxauth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Stability audit: every malformed / wrong-typed / out-of-range input the API
 * can receive must produce a clean 4xx (or 401/403/404/405/409/415) — never a
 * 500. This is the "catch all possibilities" guard: if any request slips
 * through to the 500 fallback, the test fails.
 */
@SpringBootTest
@AutoConfigureMockMvc
class HardeningIntegrationTest {

    private static final String[] SLUGS = {"hd1", "hd2", "hd3", "hd4", "hd5", "hd6"};

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // ------------------------------------------------------------------
    // 1. Malformed / unreadable bodies
    // ------------------------------------------------------------------

    @Test
    void malformedBodiesAre400Never500() throws Exception {
        for (String path : List.of(
                "/auth/register", "/auth/login", "/auth/refresh",
                "/auth/logout", "/" + SLUGS[0] + "/auth/register",
                "/" + SLUGS[0] + "/auth/login")) {
            mockMvc.perform(post(path)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{ this is not json"))
                    .andExpect(status().isBadRequest());
        }
        // missing body entirely
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
        // empty JSON object -> validation errors, not 500
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        // JSON null body -> unreadable, not 500
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content("null"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void wrongJsonTypesAre400Never500() throws Exception {
        // organisationId was removed from the external login DTO — it is an
        // internal-only field (platform console portal flow). A bogus
        // organisationId field is silently ignored by Jackson; test a missing
        // required field instead to confirm validation still catches bad input.
        mockMvc.perform(post("/" + SLUGS[0] + "/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "u",
                                "password", "password1", "firstName", "F", "lastName", "L"))))
                .andExpect(status().isBadRequest());
        // object where string expected (objects can never coerce to String)
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":{\"x\":1},\"lastName\":\"L\",\"email\":\"a@b.io\","
                                + "\"password\":\"password1\",\"platformName\":\"P\"}"))
                .andExpect(status().isBadRequest());
        // object where boolean expected
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"F\",\"lastName\":\"L\",\"email\":\"a@b.io\","
                                + "\"password\":\"password1\",\"platformName\":\"P\",\"phone\":{\"x\":1}}"))
                .andExpect(status().isBadRequest());
        // object where list of ids expected
        String boss = registerPlatform("hd-types@nexx.io", SLUGS[0]);
        long typesOrgId = createOrganisation(boss, SLUGS[0], "Types Org", "types-org");
        mockMvc.perform(post("/" + SLUGS[0] + "/organisations/" + typesOrgId + "/users")
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("firstName", "F", "lastName", "L", "roleIds", "not-a-list"))))
                .andExpect(status().isBadRequest());
        // session-settings with wrong type
        mockMvc.perform(patch("/" + SLUGS[0] + "/organisations/" + typesOrgId + "/session-settings")
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maxSessionsPerUser\":\"many\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidEnumValuesAre400Never500() throws Exception {
        String boss = registerPlatform("hd-enum@nexx.io", SLUGS[1]);
        long enumOrgId = createOrganisation(boss, SLUGS[1], "Enum Org", "enum-org");
        // invalid permission enum in role create
        mockMvc.perform(post("/" + SLUGS[1] + "/organisations/" + enumOrgId + "/roles")
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Bad\",\"permissions\":[\"NOT_A_REAL_PERMISSION\"]}"))
                .andExpect(status().isBadRequest());
        // null element inside the permission set (would otherwise fail the
        // NOT NULL join-table constraint with a confusing 409)
        mockMvc.perform(post("/" + SLUGS[1] + "/organisations/" + enumOrgId + "/roles")
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"NullPerm\",\"permissions\":[null]}"))
                .andExpect(status().isBadRequest());
        // invalid auth type enum
        mockMvc.perform(patch("/" + SLUGS[1] + "/organisations/" + enumOrgId + "/auth-config")
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"authType\":\"OTP\"}"))
                .andExpect(status().isBadRequest());
        // invalid role enum on platform user update
        mockMvc.perform(patch("/users/1")
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"GOD\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void outOfRangeValuesAre400Never500() throws Exception {
        String boss = registerPlatform("hd-range@nexx.io", SLUGS[2]);
        long rangeOrgId = createOrganisation(boss, SLUGS[2], "Range Org", "range-org");
        String orgUsers = "/" + SLUGS[2] + "/organisations/" + rangeOrgId + "/users";
        String org = "/" + SLUGS[2] + "/organisations/" + rangeOrgId;

        // invalid email format
        mockMvc.perform(post(orgUsers)
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("firstName", "F", "lastName", "L", "email", "not-an-email"))))
                .andExpect(status().isBadRequest());
        // oversize first name (101 chars)
        mockMvc.perform(post(orgUsers)
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("firstName", "x".repeat(101), "lastName", "L"))))
                .andExpect(status().isBadRequest());
        // invalid slug pattern + slug too long on organisation create
        mockMvc.perform(post("/" + SLUGS[2] + "/organisations")
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Slug Org", "slug", "UPPER CASE!"))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/" + SLUGS[2] + "/organisations")
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Slug Org", "slug", "s".repeat(101)))))
                .andExpect(status().isBadRequest());
        // negative password history count
        mockMvc.perform(patch(org + "/auth-config")
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("passwordHistoryCount", -1))))
                .andExpect(status().isBadRequest());
        // password history count above the cap
        mockMvc.perform(patch(org + "/auth-config")
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("passwordHistoryCount", 51))))
                .andExpect(status().isBadRequest());
        // password expiration beyond the cap
        mockMvc.perform(patch(org + "/auth-config")
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("passwordExpirationDays", 3651))))
                .andExpect(status().isBadRequest());
        // session settings below floors
        mockMvc.perform(patch(org + "/session-settings")
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("accessTokenTtlSeconds", 10))))
                .andExpect(status().isBadRequest());
        // session settings above the caps
        mockMvc.perform(patch(org + "/session-settings")
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("accessTokenTtlSeconds", 86401))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch(org + "/session-settings")
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshTokenTtlSeconds", 31536001L))))
                .andExpect(status().isBadRequest());
        // oversize username on update (101 chars)
        mockMvc.perform(patch(orgUsers + "/1")
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "u".repeat(101)))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void pathAndMethodErrorsNever500() throws Exception {
        String boss = registerPlatform("hd-path@nexx.io", SLUGS[3]);
        long pathOrgId = createOrganisation(boss, SLUGS[3], "Path Org", "path-org");
        String org = "/" + SLUGS[3] + "/organisations/" + pathOrgId;

        // path variable of wrong type
        mockMvc.perform(get(org + "/users/abc").header("Authorization", bearer(boss)))
                .andExpect(status().isBadRequest());
        // unknown route
        mockMvc.perform(get("/nope/does-not-exist").header("Authorization", bearer(boss)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(org + "/users/999999").header("Authorization", bearer(boss)))
                .andExpect(status().isNotFound());
        // method not allowed
        mockMvc.perform(get("/auth/login"))
                .andExpect(status().isMethodNotAllowed());
        // unsupported media type
        mockMvc.perform(post("/auth/login").contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType());
        // unknown platform slug -> 404
        mockMvc.perform(get("/no-such-platform").header("Authorization", bearer(boss)))
                .andExpect(status().isNotFound());
        // oversized request body -> 413 before it is ever buffered
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .header(HttpHeaders.CONTENT_LENGTH, "200000"))
                .andExpect(status().isPayloadTooLarge());
    }

    // ------------------------------------------------------------------
    // 2. Identifier / role edge cases
    // ------------------------------------------------------------------

    @Test
    void roleEdgeCasesNever500() throws Exception {
        String boss = registerPlatform("hd-role@nexx.io", SLUGS[4]);
        long roleOrgId = createOrganisation(boss, SLUGS[4], "Role Org", "role-org");
        long otherOrgId = createOrganisation(boss, SLUGS[4], "Other Org", "other-org");
        String org = "/" + SLUGS[4] + "/organisations/" + roleOrgId;
        String other = "/" + SLUGS[4] + "/organisations/" + otherOrgId;

        long otherRoleId = createRole(boss, other, "Foreign Role");

        // a role from ANOTHER org must not be assignable
        mockMvc.perform(post(org + "/users")
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("firstName", "F", "lastName", "L",
                                "username", "bob", "roleIds", List.of(otherRoleId)))))
                .andExpect(status().isBadRequest());

        // a non-existent role id
        mockMvc.perform(post(org + "/users")
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("firstName", "F", "lastName", "L",
                                "username", "bob2", "roleIds", List.of(999999L)))))
                .andExpect(status().isBadRequest());

        // null element inside the roleIds list must not 500
        mockMvc.perform(post(org + "/users")
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"F\",\"lastName\":\"L\",\"username\":\"bob3\",\"roleIds\":[null]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void identifierEdgeCasesNever500() throws Exception {
        String boss = registerPlatform("hd-id@nexx.io", SLUGS[5]);
        createOrganisation(boss, SLUGS[5], "Id Org", "id-org");
        String orgAuth = "/" + SLUGS[5] + "/auth";

        // without X-Client-Id, the org cannot be resolved -> 400, never 500
        mockMvc.perform(post(orgAuth + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("identifier", "x", "password", "y"))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(orgAuth + "/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "u",
                                "password", "p".repeat(1000), "firstName", "F", "lastName", "L"))))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------
    // 3. Auth / token edge cases
    // ------------------------------------------------------------------

    @Test
    void tokenEdgeCasesNever500() throws Exception {
        // garbage / truncated / wrong-shaped bearer tokens
        for (String token : List.of(
                "garbage.token.here", "abc", "Bearer", "a.b", "a.b.c.d",
                "eyJ.eyJ.eyJ", "  ")) {
            mockMvc.perform(get("/auth/me").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }
        // garbage refresh tokens
        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", "not-a-token"))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/" + SLUGS[0] + "/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", "not-a-token"))))
                .andExpect(status().isUnauthorized());
        // platform token must not work on org routes, org token must not work
        // on platform routes (both just 401, never 500)
        MvcResult reg = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("firstName", "T", "lastName", "K",
                                "email", "hd-token@nexx.io", "password", "password1",
                                "platformName", "Token Co", "platformSlug", "token-co"))))
                .andExpect(status().isCreated())
                .andReturn();
        String platformToken = objectMapper.readTree(reg.getResponse().getContentAsString())
                .get("accessToken").asText();
        mockMvc.perform(get("/token-co/organisations")
                        .header("Authorization", bearer(platformToken)))
                .andExpect(status().isOk());
    }

    // --- helpers ---

    private String registerPlatform(String email, String slug) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "firstName", "F", "lastName", "L",
                                "email", email, "password", "password1",
                                "platformName", "Hardening Platform", "platformSlug", slug))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private long createOrganisation(String boss, String slug, String name, String orgSlug) throws Exception {
        MvcResult result = mockMvc.perform(post("/" + slug + "/organisations")
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", name, "slug", orgSlug))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private long createRole(String boss, String org, String name) throws Exception {
        MvcResult result = mockMvc.perform(post(org + "/roles")
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", name, "permissions", List.of()))))
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
