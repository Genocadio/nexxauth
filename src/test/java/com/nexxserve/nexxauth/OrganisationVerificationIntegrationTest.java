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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Org-level verification through nexxbotify: OTP and magic-link flows for
 * email verification, phone verification, password reset, and OTP login.
 * nexxbotify is mocked; the OTP/link values it is asked to send are captured
 * from the {@link NexxbotifyClient} mock and reused by the "user".
 */
@SpringBootTest
@AutoConfigureMockMvc
class OrganisationVerificationIntegrationTest {

    private static final String[] SLUGS = {"v1", "v2", "v3", "v4", "v5", "v6", "v7", "v8"};

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private NexxbotifyClient nexxbotifyClient;

    @BeforeEach
    void resetMock() {
        clearInvocations(nexxbotifyClient);
        // The verification features are wired to the notification service; the
        // tests exercise the happy path, so the mock is treated as configured.
        when(nexxbotifyClient.isConfigured()).thenReturn(true);
    }

    @Test
    void emailVerificationByOtpMarksUserVerified() throws Exception {
        String platform = "/" + SLUGS[0];
        String boss = registerPlatform("ve-boss@nexx.io", SLUGS[0]);
        long orgId = createOrganisation(boss, platform, "Verify Org", "verify-org");
        String org = platform + "/organisations/" + orgId;
        String orgAuth = platform + "/auth";
        String clientKey = createClient(boss, org, "Test Client");

        enable(platform, orgId, boss, Map.of("emailVerificationEnabled", true));

        MvcResult reg = mockMvc.perform(post(orgAuth + "/register")
                        .header("X-Client-Id", clientKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "organisationId", orgId, "username", "veuser",
                                "email", "ve@example.com",
                                "password", "passw0rd1", "firstName", "V", "lastName", "E"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.emailVerified").value(false))
                .andExpect(jsonPath("$.actions[0]").value("VERIFY_EMAIL"))
                .andReturn();
        String token = objectMapper.readTree(reg.getResponse().getContentAsString()).get("accessToken").asText();

        // request an OTP to the email
        mockMvc.perform(post(orgAuth + "/verifications/request")
                        .header("X-Client-Id", clientKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "organisationId", orgId, "identifier", "ve@example.com",
                                "channel", "EMAIL", "purpose", "EMAIL_VERIFICATION", "delivery", "OTP"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.purpose").value("EMAIL_VERIFICATION"))
                .andExpect(jsonPath("$.channel").value("EMAIL"));

        String code = capturedCode();
        // submit the code
        mockMvc.perform(post(orgAuth + "/verifications/verify")
                        .header("X-Client-Id", clientKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "organisationId", orgId, "identifier", "ve@example.com",
                                "purpose", "EMAIL_VERIFICATION", "code", code))))
                .andExpect(status().isNoContent());

        // now verified
        mockMvc.perform(get(org + "/users/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailVerified").value(true));
    }

    @Test
    void magicLinkVerificationWorksOnComplete() throws Exception {
        String platform = "/" + SLUGS[1];
        String boss = registerPlatform("ve-link-boss@nexx.io", SLUGS[1]);
        long orgId = createOrganisation(boss, platform, "Link Org", "link-org");
        String org = platform + "/organisations/" + orgId;
        String orgAuth = platform + "/auth";
        String clientKey = createClient(boss, org, "Test Client");
        enable(platform, orgId, boss, Map.of("phoneVerificationEnabled", true));

        mockMvc.perform(post(orgAuth + "/register")
                        .header("X-Client-Id", clientKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "organisationId", orgId, "username", "linkuser",
                                "phone", "+15551234567", "password", "passw0rd1",
                                "firstName", "L", "lastName", "K"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.phoneVerified").value(false))
                .andExpect(jsonPath("$.actions[0]").value("VERIFY_PHONE"));

        // request a magic link for the phone
        mockMvc.perform(post(orgAuth + "/verifications/request")
                        .header("X-Client-Id", clientKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "organisationId", orgId, "identifier", "+15551234567",
                                "channel", "SMS", "purpose", "PHONE_VERIFICATION", "delivery", "LINK"))))
                .andExpect(status().isOk());

        String link = capturedLink();
        // opening the link completes verification
        mockMvc.perform(get(platform + "/auth/verifications/complete?token=" + extractToken(link)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(true));

        // a fresh login no longer surfaces the VERIFY_PHONE action
        mockMvc.perform(post(orgAuth + "/login")
                        .header("X-Client-Id", clientKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("organisationId", orgId, "identifier", "linkuser",
                                "password", "passw0rd1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.phoneVerified").value(true))
                .andExpect(jsonPath("$.actions").isEmpty());
    }

    @Test
    void passwordResetWithOtpLetsUserLoginWithNewPassword() throws Exception {
        String platform = "/" + SLUGS[2];
        String boss = registerPlatform("ve-reset-boss@nexx.io", SLUGS[2]);
        long orgId = createOrganisation(boss, platform, "Reset Org", "reset-org");
        String org = platform + "/organisations/" + orgId;
        String orgAuth = platform + "/auth";
        String clientKey = createClient(boss, org, "Test Client");
        enable(platform, orgId, boss, Map.of("passwordResetEnabled", true));

        mockMvc.perform(post(orgAuth + "/register")
                        .header("X-Client-Id", clientKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "organisationId", orgId, "username", "resetuser",
                                "email", "reset@example.com", "password", "oldpass123",
                                "firstName", "R", "lastName", "S"))))
                .andExpect(status().isCreated());

        // request a reset OTP to the email
        mockMvc.perform(post(orgAuth + "/verifications/request")
                        .header("X-Client-Id", clientKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "organisationId", orgId, "identifier", "reset@example.com",
                                "channel", "EMAIL", "purpose", "PASSWORD_RESET", "delivery", "OTP"))))
                .andExpect(status().isOk());

        String code = capturedCode();
        // confirm the reset with a new password
        mockMvc.perform(post(orgAuth + "/password-reset/confirm")
                        .header("X-Client-Id", clientKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "organisationId", orgId, "identifier", "reset@example.com",
                                "channel", "EMAIL", "token", code, "newPassword", "newpass456"))))
                .andExpect(status().isNoContent());

        // old password no longer works, new one does
        mockMvc.perform(post(orgAuth + "/login")
                        .header("X-Client-Id", clientKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("organisationId", orgId, "identifier", "resetuser",
                                "password", "oldpass123"))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(orgAuth + "/login")
                        .header("X-Client-Id", clientKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("organisationId", orgId, "identifier", "resetuser",
                                "password", "newpass456"))))
                .andExpect(status().isOk());
    }

    @Test
    void otpLoginIssuesSessionWhenCodeMatches() throws Exception {
        String platform = "/" + SLUGS[3];
        String boss = registerPlatform("ve-otp-boss@nexx.io", SLUGS[3]);
        long orgId = createOrganisation(boss, platform, "Otp Org", "otp-org");
        String org = platform + "/organisations/" + orgId;
        String orgAuth = platform + "/auth";
        String clientKey = createClient(boss, org, "Test Client");
        enable(platform, orgId, boss, Map.of("otpLoginEnabled", true));

        mockMvc.perform(post(orgAuth + "/register")
                        .header("X-Client-Id", clientKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "organisationId", orgId, "username", "otpuser",
                                "email", "otp@example.com", "password", "passw0rd1",
                                "firstName", "O", "lastName", "P"))))
                .andExpect(status().isCreated())
                .andReturn();

        // request a login OTP (the flow is public)
        mockMvc.perform(post(orgAuth + "/verifications/request")
                        .header("X-Client-Id", clientKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "organisationId", orgId, "identifier", "otp@example.com",
                                "channel", "EMAIL", "purpose", "LOGIN_OTP", "delivery", "OTP"))))
                .andExpect(status().isOk());

        String code = capturedCode();
        // sign in with the OTP instead of the password
        MvcResult login = mockMvc.perform(post(orgAuth + "/login")
                        .header("X-Client-Id", clientKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "organisationId", orgId, "identifier", "otp@example.com",
                                "identifierType", "EMAIL", "authType", "OTP", "otpCode", code))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.username").value("otpuser"))
                .andReturn();
        String otpToken = objectMapper.readTree(login.getResponse().getContentAsString()).get("accessToken").asText();

        mockMvc.perform(get(org + "/users/me").header("Authorization", bearer(otpToken)))
                .andExpect(status().isOk());
    }

    @Test
    void verificationRequestIsLockedWhenNotifierNotConfigured() throws Exception {
        // Simulate a deployment without NEXXBOTIFY_URL: the feature is locked
        // and the request is rejected up front instead of failing at send time.
        when(nexxbotifyClient.isConfigured()).thenReturn(false);
        String platform = "/" + SLUGS[5];
        String boss = registerPlatform("ve-lock-boss@nexx.io", SLUGS[5]);
        long orgId = createOrganisation(boss, platform, "Lock Org", "lock-org");
        String org = platform + "/organisations/" + orgId;
        String orgAuth = platform + "/auth";
        String clientKey = createClient(boss, org, "Test Client");
        enable(platform, orgId, boss, Map.of("emailVerificationEnabled", true));

        mockMvc.perform(post(orgAuth + "/verifications/request")
                        .header("X-Client-Id", clientKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "organisationId", orgId, "identifier", "lock@example.com",
                                "channel", "EMAIL", "purpose", "EMAIL_VERIFICATION", "delivery", "OTP"))))
                .andExpect(status().isBadRequest());

        verify(nexxbotifyClient, never()).send(any(), any(), anyString(), anyMap());

        // the admin-facing auth-config surfaces the locked state: verification
        // features show as unavailable so admins know why they fail
        mockMvc.perform(get(platform + "/organisations/" + orgId + "/auth-config")
                        .header("Authorization", bearer(boss)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationServiceAvailable").value(false));
    }

    @Test
    void featureGateReturnsBadRequestWhenDisabled() throws Exception {
        String platform = "/" + SLUGS[7];
        String boss = registerPlatform("ve-gate-boss@nexx.io", SLUGS[7]);
        long orgId = createOrganisation(boss, platform, "Gate Org", "gate-org");
        String org = platform + "/organisations/" + orgId;
        String orgAuth = platform + "/auth";
        String clientKey = createClient(boss, org, "Test Client");

        // org feature is off: request is rejected
        mockMvc.perform(post(orgAuth + "/verifications/request")
                        .header("X-Client-Id", clientKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "organisationId", orgId, "identifier", "x@example.com",
                                "channel", "EMAIL", "purpose", "PASSWORD_RESET", "delivery", "OTP"))))
                .andExpect(status().isBadRequest());
    }

    // --- helpers ---

    private void enable(String platform, long orgId, String boss, Map<String, Object> flags) throws Exception {
        mockMvc.perform(patch(platform + "/organisations/" + orgId + "/auth-config")
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(flags)))
                .andExpect(status().isOk());
    }

    @SuppressWarnings("unchecked")
    private String capturedCode() {
        ArgumentCaptor<Map<String, Object>> variables = ArgumentCaptor.forClass(Map.class);
        verify(nexxbotifyClient).send(org.mockito.ArgumentMatchers.eq(VerificationDelivery.OTP),
                org.mockito.ArgumentMatchers.any(VerificationChannel.class),
                org.mockito.ArgumentMatchers.anyString(), variables.capture());
        return (String) variables.getValue().get("code");
    }

    @SuppressWarnings("unchecked")
    private String capturedLink() {
        ArgumentCaptor<Map<String, Object>> variables = ArgumentCaptor.forClass(Map.class);
        verify(nexxbotifyClient).send(org.mockito.ArgumentMatchers.eq(VerificationDelivery.LINK),
                org.mockito.ArgumentMatchers.any(VerificationChannel.class),
                org.mockito.ArgumentMatchers.anyString(), variables.capture());
        return (String) variables.getValue().get("link");
    }

    private String extractToken(String link) {
        return link.substring(link.indexOf("token=") + "token=".length());
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

    private String registerPlatform(String email, String slug) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "firstName", "F", "lastName", "L",
                                "email", email, "password", "password1",
                                "platformName", "Verification Platform", "platformSlug", slug))))
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