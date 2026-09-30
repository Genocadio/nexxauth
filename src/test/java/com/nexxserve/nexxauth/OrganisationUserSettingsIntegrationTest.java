package com.nexxserve.nexxauth;

import com.nexxserve.nexxauth.entity.VerificationChannel;
import com.nexxserve.nexxauth.entity.VerificationDelivery;
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
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The per-user settings surface an administrator gets from the users table:
 * manual verification overrides, an administrator-triggered verification send
 * to one of the user's own addresses, a password reset the user completes
 * themselves, and the per-user login method that decides whether a password, a
 * one-time code, or either is accepted.
 *
 * <p>The notification service is mocked; the code it is asked to deliver is
 * captured and replayed as the "user" receiving it.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OrganisationUserSettingsIntegrationTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

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
    void administratorCanMarkAnEmailVerifiedAndUnverifiedByHand() throws Exception {
        Ctx c = ctx();
        long userId = createUser(c, "manual@example.com", null, null, "manpass1");
        long emailId = primaryEmailId(c, userId);

        mockMvc.perform(get(c.org() + "/users/" + userId).header("Authorization", bearer(c.boss())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emails[0].verified").value(false));

        mockMvc.perform(patch(c.org() + "/users/" + userId + "/emails/" + emailId + "/verified")
                        .header("Authorization", bearer(c.boss()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("verified", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emails[0].verified").value(true));

        mockMvc.perform(get(c.org() + "/users/" + userId).header("Authorization", bearer(c.boss())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emails[0].verified").value(true));

        // and the override is reversible, so a mistaken tick is not permanent
        mockMvc.perform(patch(c.org() + "/users/" + userId + "/emails/" + emailId + "/verified")
                        .header("Authorization", bearer(c.boss()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("verified", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emails[0].verified").value(false));
    }

    @Test
    void administratorCanSendVerificationToAUsersOwnEmail() throws Exception {
        Ctx c = ctx();
        long userId = createUser(c, "sendme@example.com", null, null, "sendpass1");
        long emailId = primaryEmailId(c, userId);
        enable(c, Map.of("emailVerificationEnabled", true));

        mockMvc.perform(post(c.org() + "/users/" + userId + "/verifications")
                        .header("Authorization", bearer(c.boss()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("channel", "EMAIL", "emailId", emailId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.purpose").value("EMAIL_VERIFICATION"))
                .andExpect(jsonPath("$.channel").value("EMAIL"));

        // the code went to that address, and the user can confirm it
        String code = capturedCode();
        mockMvc.perform(post(c.orgAuth() + "/verifications/verify")
                        .header("X-Client-Id", c.clientKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "organisationId", c.orgId(), "identifier", "sendme@example.com",
                                "purpose", "EMAIL_VERIFICATION", "code", code))))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(c.org() + "/users/" + userId).header("Authorization", bearer(c.boss())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emails[0].verified").value(true));
    }

    @Test
    void verificationSentToAPhoneIsAPhoneVerification() throws Exception {
        Ctx c = ctx();
        long userId = createUser(c, null, "+250788000111", null, "smspass1");
        long phoneId = primaryPhoneId(c, userId);
        enable(c, Map.of("phoneVerificationEnabled", true));

        // An SMS delivery is a phone verification; sending EMAIL_VERIFICATION
        // over SMS is a purpose/channel mismatch and must not be attempted.
        mockMvc.perform(post(c.org() + "/users/" + userId + "/verifications")
                        .header("Authorization", bearer(c.boss()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("channel", "SMS", "phoneId", phoneId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.purpose").value("PHONE_VERIFICATION"))
                .andExpect(jsonPath("$.channel").value("SMS"));
    }

    @Test
    void administratorCanStartAPasswordResetTheUserFinishesThemselves() throws Exception {
        Ctx c = ctx();
        long userId = createUser(c, "resetme@example.com", null, "resetuser", "oldpass1");
        enable(c, Map.of("passwordResetEnabled", true));

        MvcResult req = mockMvc.perform(post(c.org() + "/users/" + userId + "/password-reset")
                        .header("Authorization", bearer(c.boss()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();

        String actionToken = objectMapper.readTree(req.getResponse().getContentAsString()).get("accessToken").asText();
        String code = capturedCode();

        // the administrator cannot finish it for them: the action token is the
        // user's, and the new password is chosen by the user
        mockMvc.perform(post(c.orgAuth() + "/password-reset/confirm")
                        .header("X-Client-Id", c.clientKey())
                        .header("Authorization", bearer(actionToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "organisationId", c.orgId(), "token", code, "newPassword", "brandnew1"))))
                .andExpect(status().isNoContent());

        mockMvc.perform(post(c.orgAuth() + "/login")
                        .header("X-Client-Id", c.clientKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("organisationId", c.orgId(), "identifier", "resetuser",
                                "identifierType", "USERNAME", "password", "oldpass1"))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post(c.orgAuth() + "/login")
                        .header("X-Client-Id", c.clientKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("organisationId", c.orgId(), "identifier", "resetuser",
                                "identifierType", "USERNAME", "password", "brandnew1"))))
                .andExpect(status().isOk());
    }

    @Test
    void clearingThePasswordLeavesTheUserAbleToSignInWithACode() throws Exception {
        Ctx c = ctx();
        long userId = createUser(c, "clearme@example.com", null, "clearuser", "oldpass1");
        enable(c, Map.of("otpLoginEnabled", true));

        // before: a password, and a code, both work
        mockMvc.perform(get(c.org() + "/users/" + userId).header("Authorization", bearer(c.boss())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loginMethod").value("PASSWORD_OR_OTP"))
                .andExpect(jsonPath("$.authTypes[0]").value("PASSWORD"))
                .andExpect(jsonPath("$.authTypes[1]").value("OTP"));

        // removing the password must not lock the user out: they keep access
        // through a one-time code
        mockMvc.perform(patch(c.org() + "/users/" + userId)
                        .header("Authorization", bearer(c.boss()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("password", ""))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loginMethod").value("OTP"))
                .andExpect(jsonPath("$.authTypes.length()").value(1))
                .andExpect(jsonPath("$.authTypes[0]").value("OTP"));

        mockMvc.perform(post(c.orgAuth() + "/login")
                        .header("X-Client-Id", c.clientKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("organisationId", c.orgId(), "identifier", "clearuser",
                                "identifierType", "USERNAME", "password", "oldpass1"))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post(c.orgAuth() + "/verifications/request")
                        .header("X-Client-Id", c.clientKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "organisationId", c.orgId(), "identifier", "clearme@example.com",
                                "channel", "EMAIL", "purpose", "LOGIN_OTP", "delivery", "OTP"))))
                .andExpect(status().isOk());

        String code = capturedCode();
        mockMvc.perform(post(c.orgAuth() + "/login")
                        .header("X-Client-Id", c.clientKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("organisationId", c.orgId(), "identifier", "clearme@example.com",
                                "identifierType", "EMAIL", "authType", "OTP", "otpCode", code))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void passwordOnlyUserCannotSignInWithACodeButEitherUserCan() throws Exception {
        Ctx c = ctx();
        long userId = createUser(c, "method@example.com", null, "methoduser", "methodpass1");
        enable(c, Map.of("otpLoginEnabled", true));

        // restrict this one user to their password
        mockMvc.perform(patch(c.org() + "/users/" + userId)
                        .header("Authorization", bearer(c.boss()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("loginMethod", "PASSWORD"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loginMethod").value("PASSWORD"))
                .andExpect(jsonPath("$.authTypes.length()").value(1))
                .andExpect(jsonPath("$.authTypes[0]").value("PASSWORD"));

        mockMvc.perform(post(c.orgAuth() + "/verifications/request")
                        .header("X-Client-Id", c.clientKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "organisationId", c.orgId(), "identifier", "method@example.com",
                                "channel", "EMAIL", "purpose", "LOGIN_OTP", "delivery", "OTP"))))
                .andExpect(status().isOk());

        // the code is delivered but refused for a password-only user
        String code = capturedCode();
        mockMvc.perform(post(c.orgAuth() + "/login")
                        .header("X-Client-Id", c.clientKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("organisationId", c.orgId(), "identifier", "method@example.com",
                                "identifierType", "EMAIL", "authType", "OTP", "otpCode", code))))
                .andExpect(status().isUnauthorized());

        // the password still works for them
        mockMvc.perform(post(c.orgAuth() + "/login")
                        .header("X-Client-Id", c.clientKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("organisationId", c.orgId(), "identifier", "methoduser",
                                "identifierType", "USERNAME", "password", "methodpass1"))))
                .andExpect(status().isOk());

        // and widening the method back to either restores the code sign-in.
        // The refused attempt above never checked the code, so the challenge is
        // still open and this very code becomes usable — the per-user method is
        // the only thing that changed.
        mockMvc.perform(patch(c.org() + "/users/" + userId)
                        .header("Authorization", bearer(c.boss()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("loginMethod", "PASSWORD_OR_OTP"))))
                .andExpect(status().isOk());

        mockMvc.perform(post(c.orgAuth() + "/login")
                        .header("X-Client-Id", c.clientKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("organisationId", c.orgId(), "identifier", "method@example.com",
                                "identifierType", "EMAIL", "authType", "OTP", "otpCode", code))))
                .andExpect(status().isOk());
    }

    @Test
    void aUserCreatedWithNoPasswordIsInertUntilConfigured() throws Exception {
        Ctx c = ctx();

        // a placeholder is not a login: it reports no usable credential, which
        // is what stops it signing in
        mockMvc.perform(post(c.org() + "/users")
                        .header("Authorization", bearer(c.boss()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "firstName", "P", "lastName", "L", "username", "placeholder"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.loginMethod").value("PASSWORD"))
                .andExpect(jsonPath("$.authTypes").isEmpty());
    }

    @Test
    void anotherPlatformsAdministratorCannotTouchTheseSettings() throws Exception {
        Ctx c = ctx();
        long userId = createUser(c, "perm@example.com", null, "permuser", "permpass1");
        long emailId = primaryEmailId(c, userId);
        String outsider = registerPlatform("outsider@nexx.io", "set" + SEQ.incrementAndGet());

        mockMvc.perform(patch(c.org() + "/users/" + userId + "/emails/" + emailId + "/verified")
                        .header("Authorization", bearer(outsider))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("verified", true))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post(c.org() + "/users/" + userId + "/verifications")
                        .header("Authorization", bearer(outsider))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("channel", "EMAIL", "emailId", emailId))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post(c.org() + "/users/" + userId + "/password-reset")
                        .header("Authorization", bearer(outsider))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of())))
                .andExpect(status().isForbidden());
    }

    // ---- fixtures -------------------------------------------------------

    private record Ctx(String boss, String platform, String orgSlug, long orgId, String clientKey) {
        String org() {
            return platform + "/organisations/" + orgId;
        }

        String orgAuth() {
            return platform + "/auth";
        }
    }

    /** A fresh platform + organisation + client, so each test owns its data. */
    private Ctx ctx() throws Exception {
        int n = SEQ.incrementAndGet();
        String platform = "/set" + n;
        String boss = registerPlatform("set-" + n + "-boss@nexx.io", "set" + n);
        long orgId = createOrganisation(boss, platform, "Settings Org " + n, "set-org-" + n);
        String org = platform + "/organisations/" + orgId;
        String clientKey = createClient(boss, org, "Test Client");
        return new Ctx(boss, platform, "set-org-" + n, orgId, clientKey);
    }

    /** @param phone E.164, or null. @param password null creates a placeholder. */
    private long createUser(Ctx c, String email, String phone, String username, String password) throws Exception {
        java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("firstName", "F");
        body.put("lastName", "L");
        if (email != null) body.put("email", email);
        if (phone != null) body.put("phone", phone);
        if (username != null) body.put("username", username);
        if (password != null) body.put("password", password);
        return getId(mockMvc.perform(post(c.org() + "/users")
                        .header("Authorization", bearer(c.boss()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isCreated())
                .andReturn());
    }

    private long primaryEmailId(Ctx c, long userId) throws Exception {
        return objectMapper.readTree(mockMvc.perform(get(c.org() + "/users/" + userId)
                        .header("Authorization", bearer(c.boss())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("emails").get(0).get("id").asLong();
    }

    private long primaryPhoneId(Ctx c, long userId) throws Exception {
        return objectMapper.readTree(mockMvc.perform(get(c.org() + "/users/" + userId)
                        .header("Authorization", bearer(c.boss())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("phones").get(0).get("id").asLong();
    }

    private void enable(Ctx c, Map<String, Object> body) throws Exception {
        Map<String, Object> all = new java.util.LinkedHashMap<>(body);
        mockMvc.perform(patch(c.org() + "/auth-config")
                        .header("Authorization", bearer(c.boss()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(all)))
                .andExpect(status().isOk());
    }

    private String capturedCode() {
        ArgumentCaptor<Map<String, Object>> variables = ArgumentCaptor.forClass(Map.class);
        verify(nexxbotifyClient).sendForOrganisation(any(),
                org.mockito.ArgumentMatchers.eq(VerificationDelivery.OTP),
                org.mockito.ArgumentMatchers.any(VerificationChannel.class),
                anyString(), variables.capture());
        return (String) variables.getValue().get("code");
    }

    private long getId(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private String registerPlatform(String email, String slug) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "firstName", "F", "lastName", "L",
                                "email", email, "password", "password1",
                                "platformName", "Settings Platform", "platformSlug", slug))))
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

    private String createClient(String boss, String org, String name) throws Exception {
        MvcResult result = mockMvc.perform(post(org + "/clients")
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", name, "type", "WEB"))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("clientKey").asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
