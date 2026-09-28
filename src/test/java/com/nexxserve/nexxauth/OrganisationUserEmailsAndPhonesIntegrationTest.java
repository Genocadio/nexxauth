package com.nexxserve.nexxauth;

import com.nexxserve.nexxauth.service.NexxbotifyClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OrganisationUserEmailsAndPhonesIntegrationTest {

    private static final String[] SLUGS = {"ep1", "ep2", "ep3", "ep4", "ep5"};

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private NexxbotifyClient nexxbotifyClient;

    @BeforeEach
    void resetMock() {
        clearInvocations(nexxbotifyClient);
        when(nexxbotifyClient.isConfigured()).thenReturn(true);
    }

    @Test
    void multiEmailAndPhoneManagementLifecycle() throws Exception {
        String platform = "/" + SLUGS[0];
        String boss = registerPlatform("ep-boss@nexx.io", SLUGS[0]);
        long orgId = createOrganisation(boss, platform, "EP Org", "ep-org");
        String org = platform + "/organisations/" + orgId;
        String orgAuth = platform + "/auth";
        mockMvc.perform(patch(org)
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("emailCanLogin", true, "phoneCanLogin", true))))
                .andExpect(status().isOk());
        String clientKey = createClient(boss, org, "EP Client");

        // 1. Register a user with primary email and primary phone
        MvcResult reg = mockMvc.perform(post(orgAuth + "/register")
                        .header("X-Client-Id", clientKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "organisationId", orgId,
                                "username", "alice",
                                "email", "alice@example.com",
                                "phone", "+15551111111",
                                "password", "Secret123#",
                                "firstName", "Alice",
                                "lastName", "Smith"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.email").value("alice@example.com"))
                .andExpect(jsonPath("$.user.phone").value("+15551111111"))
                .andExpect(jsonPath("$.user.emails.length()").value(1))
                .andExpect(jsonPath("$.user.emails[0].email").value("alice@example.com"))
                .andExpect(jsonPath("$.user.emails[0].isPrimary").value(true))
                .andExpect(jsonPath("$.user.emails[0].verified").value(false))
                .andExpect(jsonPath("$.user.phones.length()").value(1))
                .andExpect(jsonPath("$.user.phones[0].phone").value("+15551111111"))
                .andExpect(jsonPath("$.user.phones[0].isPrimary").value(true))
                .andExpect(jsonPath("$.user.phones[0].verified").value(false))
                .andReturn();

        JsonNode regBody = objectMapper.readTree(reg.getResponse().getContentAsString());
        long userId = regBody.get("user").get("id").asLong();
        long email1Id = regBody.get("user").get("emails").get(0).get("id").asLong();
        long phone1Id = regBody.get("user").get("phones").get(0).get("id").asLong();
        String userToken = regBody.get("accessToken").asText();

        // 2. Admin adds a secondary email
        MvcResult addEmailRes = mockMvc.perform(post(org + "/users/" + userId + "/emails")
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "alice.work@example.com", "isPrimary", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emails.length()").value(2))
                .andExpect(jsonPath("$.email").value("alice@example.com")) // primary unchanged
                .andReturn();

        JsonNode emailResBody = objectMapper.readTree(addEmailRes.getResponse().getContentAsString());
        long email2Id = emailResBody.get("emails").get(1).get("id").asLong();

        // 3. User self-service adds a secondary phone
        MvcResult addPhoneRes = mockMvc.perform(post(org + "/users/me/phones")
                        .header("Authorization", bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("phone", "+15552222222", "isPrimary", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phones.length()").value(2))
                .andExpect(jsonPath("$.phone").value("+15551111111")) // primary unchanged
                .andReturn();

        JsonNode phoneResBody = objectMapper.readTree(addPhoneRes.getResponse().getContentAsString());
        long phone2Id = phoneResBody.get("phones").get(1).get("id").asLong();

        // 4. Switch primary email to alice.work@example.com
        mockMvc.perform(put(org + "/users/" + userId + "/emails/" + email2Id + "/primary")
                        .header("Authorization", bearer(boss)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("alice.work@example.com"))
                .andExpect(jsonPath("$.emails[0].email").value("alice.work@example.com"))
                .andExpect(jsonPath("$.emails[0].isPrimary").value(true))
                .andExpect(jsonPath("$.emails[1].isPrimary").value(false));

        // 5. Switch primary phone to +15552222222
        mockMvc.perform(put(org + "/users/me/phones/" + phone2Id + "/primary")
                        .header("Authorization", bearer(userToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("+15552222222"))
                .andExpect(jsonPath("$.phones[0].phone").value("+15552222222"))
                .andExpect(jsonPath("$.phones[0].isPrimary").value(true))
                .andExpect(jsonPath("$.phones[1].isPrimary").value(false));

        // 6. Login using secondary (non-primary) email alice@example.com
        mockMvc.perform(post(orgAuth + "/login")
                        .header("X-Client-Id", clientKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "organisationId", orgId,
                                "identifier", "alice@example.com",
                                "identifierType", "EMAIL",
                                "password", "Secret123#"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(userId));

        // 7. Login using primary email alice.work@example.com
        mockMvc.perform(post(orgAuth + "/login")
                        .header("X-Client-Id", clientKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "organisationId", orgId,
                                "identifier", "alice.work@example.com",
                                "identifierType", "EMAIL",
                                "password", "Secret123#"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(userId));

        // 8. Login using secondary phone +15551111111
        mockMvc.perform(post(orgAuth + "/login")
                        .header("X-Client-Id", clientKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "organisationId", orgId,
                                "identifier", "+15551111111",
                                "identifierType", "PHONE",
                                "password", "Secret123#"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(userId));

        // 9. Login using primary phone +15552222222
        mockMvc.perform(post(orgAuth + "/login")
                        .header("X-Client-Id", clientKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "organisationId", orgId,
                                "identifier", "+15552222222",
                                "identifierType", "PHONE",
                                "password", "Secret123#"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(userId));

        // 10. Delete an email
        mockMvc.perform(delete(org + "/users/" + userId + "/emails/" + email1Id)
                        .header("Authorization", bearer(boss)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emails.length()").value(1))
                .andExpect(jsonPath("$.email").value("alice.work@example.com"));

        // 11. Delete a phone
        mockMvc.perform(delete(org + "/users/me/phones/" + phone1Id)
                        .header("Authorization", bearer(userToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phones.length()").value(1))
                .andExpect(jsonPath("$.phone").value("+15552222222"));
    }

    @Test
    void uniquenessPerOrganisationAndCrossOrgIndependence() throws Exception {
        String platform = "/" + SLUGS[1];
        String boss = registerPlatform("ep-unique-boss@nexx.io", SLUGS[1]);
        long org1Id = createOrganisation(boss, platform, "Unique Org 1", "unique-org-1");
        long org2Id = createOrganisation(boss, platform, "Unique Org 2", "unique-org-2");
        String org1 = platform + "/organisations/" + org1Id;
        String org2 = platform + "/organisations/" + org2Id;
        String clientKey1 = createClient(boss, org1, "Client 1");
        String clientKey2 = createClient(boss, org2, "Client 2");

        // User 1 in Org 1
        mockMvc.perform(post(platform + "/auth/register")
                        .header("X-Client-Id", clientKey1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "organisationId", org1Id,
                                "username", "bob",
                                "email", "shared@example.com",
                                "phone", "+15559999999",
                                "password", "Secret123#",
                                "firstName", "Bob",
                                "lastName", "Builder"))))
                .andExpect(status().isCreated());

        // User 2 in Org 1 attempting to use same email -> 409 Conflict
        mockMvc.perform(post(platform + "/auth/register")
                        .header("X-Client-Id", clientKey1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "organisationId", org1Id,
                                "username", "charlie",
                                "email", "shared@example.com",
                                "password", "Secret123#",
                                "firstName", "Charlie",
                                "lastName", "Brown"))))
                .andExpect(status().isConflict());

        // User 2 in Org 1 attempting to use same phone -> 409 Conflict
        mockMvc.perform(post(platform + "/auth/register")
                        .header("X-Client-Id", clientKey1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "organisationId", org1Id,
                                "username", "charlie",
                                "phone", "+15559999999",
                                "password", "Secret123#",
                                "firstName", "Charlie",
                                "lastName", "Brown"))))
                .andExpect(status().isConflict());

        // User in Org 2 using the SAME email and phone -> 201 Created (isolated per org)
        mockMvc.perform(post(platform + "/auth/register")
                        .header("X-Client-Id", clientKey2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "organisationId", org2Id,
                                "username", "bob-org2",
                                "email", "shared@example.com",
                                "phone", "+15559999999",
                                "password", "Secret123#",
                                "firstName", "Bob",
                                "lastName", "Org2"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.email").value("shared@example.com"))
                .andExpect(jsonPath("$.user.phone").value("+15559999999"));
    }

    @Test
    void verificationFlowUpdatesIndividualEmailAndPhoneTimestamps() throws Exception {
        String platform = "/" + SLUGS[2];
        String boss = registerPlatform("ep-ver-boss@nexx.io", SLUGS[2]);
        long orgId = createOrganisation(boss, platform, "Ver Org", "ver-org");
        String org = platform + "/organisations/" + orgId;
        String orgAuth = platform + "/auth";
        String clientKey = createClient(boss, org, "Ver Client");

        enable(platform, orgId, boss, Map.of(
                "emailVerificationEnabled", true,
                "phoneVerificationEnabled", true));

        // Register user
        MvcResult reg = mockMvc.perform(post(orgAuth + "/register")
                        .header("X-Client-Id", clientKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "organisationId", orgId,
                                "username", "dan",
                                "email", "dan@example.com",
                                "phone", "+15553333333",
                                "password", "Secret123#",
                                "firstName", "Dan",
                                "lastName", "Miller"))))
                .andExpect(status().isCreated())
                .andReturn();

        long userId = objectMapper.readTree(reg.getResponse().getContentAsString()).get("user").get("id").asLong();

        // Add a secondary email to Dan
        mockMvc.perform(post(org + "/users/" + userId + "/emails")
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "dan.alt@example.com"))))
                .andExpect(status().isOk());

        // Request OTP verification specifically for secondary email
        mockMvc.perform(post(orgAuth + "/verifications/request")
                        .header("X-Client-Id", clientKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "organisationId", orgId,
                                "identifier", "dan.alt@example.com",
                                "channel", "EMAIL",
                                "purpose", "EMAIL_VERIFICATION",
                                "delivery", "OTP"))))
                .andExpect(status().isOk());

        String code = capturedCode();
        // Submit code
        mockMvc.perform(post(orgAuth + "/verifications/verify")
                        .header("X-Client-Id", clientKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "organisationId", orgId,
                                "identifier", "dan.alt@example.com",
                                "purpose", "EMAIL_VERIFICATION",
                                "code", code))))
                .andExpect(status().isNoContent());

        // Check user profile: secondary email is verified, primary email is not verified
        MvcResult userRes = mockMvc.perform(get(org + "/users/" + userId)
                        .header("Authorization", bearer(boss)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailVerified").value(false)) // primary is dan@example.com which is unverified
                .andExpect(jsonPath("$.emails[0].email").value("dan@example.com"))
                .andExpect(jsonPath("$.emails[0].verified").value(false))
                .andExpect(jsonPath("$.emails[1].email").value("dan.alt@example.com"))
                .andExpect(jsonPath("$.emails[1].verified").value(true))
                .andExpect(jsonPath("$.emails[1].verifiedAt").isNotEmpty())
                .andReturn();
    }

    private String capturedCode() {
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(nexxbotifyClient).sendForOrganisation(any(), any(), any(), anyString(), payloadCaptor.capture());
        Object code = payloadCaptor.getValue().get("code");
        assertThat(code).isNotNull();
        clearInvocations(nexxbotifyClient);
        when(nexxbotifyClient.isConfigured()).thenReturn(true);
        return String.valueOf(code);
    }

    private void enable(String platform, long orgId, String token, Map<String, Object> fields) throws Exception {
        mockMvc.perform(patch(platform + "/organisations/" + orgId + "/auth-config")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(fields)))
                .andExpect(status().isOk());
    }

    private String registerPlatform(String email, String slug) throws Exception {
        MvcResult res = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", email, "password", "password1",
                                "firstName", "F", "lastName", "L",
                                "platformName", "Platform " + slug, "platformSlug", slug))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private long createOrganisation(String token, String platform, String name, String slug) throws Exception {
        MvcResult res = mockMvc.perform(post(platform + "/organisations")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", name, "slug", slug))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();
    }

    private String createClient(String token, String org, String name) throws Exception {
        MvcResult res = mockMvc.perform(post(org + "/clients")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", name, "type", "WEB"))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("clientKey").asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String json(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
