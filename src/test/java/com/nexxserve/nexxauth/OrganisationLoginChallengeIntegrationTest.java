package com.nexxserve.nexxauth;

import com.nexxserve.nexxauth.entity.VerificationChannel;
import com.nexxserve.nexxauth.entity.VerificationDelivery;
import com.nexxserve.nexxauth.entity.VerificationPurpose;
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
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Server-sent login challenges: 2FA after password, verify-at-next-login
 * flags set by an admin, register-required verification gating, and the
 * organisation-wide verification-mode default. The OTP/link values are
 * captured from the mocked {@link NexxbotifyClient} and replayed as the code
 * or link the "user" receives.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OrganisationLoginChallengeIntegrationTest {

    private static final String[] SLUGS = {"c1", "c2", "c3", "c4", "c5", "c6"};

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private NexxbotifyClient nexxbotifyClient;

    @BeforeEach
    void resetMock() {
        clearInvocations(nexxbotifyClient);
        // The login-challenge flows deliver OTPs through the notification
        // service; the tests exercise the happy path, so the mock is treated
        // as configured.
        when(nexxbotifyClient.isConfigured()).thenReturn(true);
    }

    @Test
    void twoFactorPasswordLoginSendsChallengeThenTradesCodeForSession() throws Exception {
        String platform = "/" + SLUGS[0];
        String boss = registerPlatform("ch2fa-boss@nexx.io", SLUGS[0]);
        long orgId = createOrganisation(boss, platform, "TwoFactor Org", "twofactor-org");
        String org = platform + "/organisations/" + orgId;
        String orgAuth = platform + "/auth";
        String clientKey = createClient(boss, org, "Test Client");
        enable(platform, orgId, boss, Map.of("twoFactorEnabled", true));

        registerUser(orgAuth, clientKey, orgId, "ch2fa", "ch2fa@example.com", "passw0rd1");

        // password alone is no longer enough: the server sends an OTP and the
        // response carries a challenge instead of tokens
        MvcResult login = mockMvc.perform(post(orgAuth + "/login")
                        .header("X-Client-Id", clientKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("organisationId", orgId, "identifier", "ch2fa",
                                "password", "passw0rd1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.challenge.challengeToken").isNotEmpty())
                .andExpect(jsonPath("$.challenge.purpose").value(VerificationPurpose.TWO_FACTOR.name()))
                .andExpect(jsonPath("$.challenge.channel").value(VerificationChannel.EMAIL.name()))
                .andExpect(jsonPath("$.challenge.delivery").value(VerificationDelivery.OTP.name()))
                .andReturn();

        String challengeToken = objectMapper.readTree(login.getResponse().getContentAsString())
                .get("challenge").get("challengeToken").asText();
        String code = capturedCode();

        // solving the challenge issues the full session
        MvcResult solved = mockMvc.perform(post(orgAuth + "/challenges/verify")
                        .header("X-Client-Id", clientKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("challengeToken", challengeToken, "code", code))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.challenge").doesNotExist())
                .andExpect(jsonPath("$.user.username").value("ch2fa"))
                .andReturn();

        String token = objectMapper.readTree(solved.getResponse().getContentAsString()).get("accessToken").asText();
        mockMvc.perform(get(org + "/users/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
    }

    @Test
    void wrongCodeOnTwoFactorKeepsChallengeOpenAndThenSucceeds() throws Exception {
        String platform = "/" + SLUGS[4];
        String boss = registerPlatform("ch2fa-wrong-boss@nexx.io", SLUGS[4]);
        long orgId = createOrganisation(boss, platform, "Wrong 2fa Org", "wrong2fa-org");
        String org = platform + "/organisations/" + orgId;
        String orgAuth = platform + "/auth";
        String clientKey = createClient(boss, org, "Test Client");
        enable(platform, orgId, boss, Map.of("twoFactorEnabled", true));

        registerUser(orgAuth, clientKey, orgId, "ch2fa2", "ch2fa2@example.com", "passw0rd1");

        MvcResult login = mockMvc.perform(post(orgAuth + "/login")
                        .header("X-Client-Id", clientKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("organisationId", orgId, "identifier", "ch2fa2",
                                "password", "passw0rd1"))))
                .andExpect(status().isOk())
                .andReturn();
        String challengeToken = objectMapper.readTree(login.getResponse().getContentAsString())
                .get("challenge").get("challengeToken").asText();
        String code = capturedCode();
        clearInvocations(nexxbotifyClient);

        // a wrong code is rejected...
        mockMvc.perform(post(orgAuth + "/challenges/verify")
                        .header("X-Client-Id", clientKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("challengeToken", challengeToken, "code", "000000"))))
                .andExpect(status().isUnauthorized());

        // ...the right code still completes the login
        mockMvc.perform(post(orgAuth + "/challenges/verify")
                        .header("X-Client-Id", clientKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("challengeToken", challengeToken, "code", code))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void adminSetVerifyAtNextLoginBlocksPasswordLoginUntilEmailProven() throws Exception {
        String platform = "/" + SLUGS[1];
        String boss = registerPlatform("ch-next-boss@nexx.io", SLUGS[1]);
        long orgId = createOrganisation(boss, platform, "NextLogin Org", "nextlogin-org");
        String org = platform + "/organisations/" + orgId;
        String orgAuth = platform + "/auth";
        String clientKey = createClient(boss, org, "Test Client");

        MvcResult reg = mockMvc.perform(post(orgAuth + "/register")
                        .header("X-Client-Id", clientKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "organisationId", orgId, "username", "chnext",
                                "email", "chnext@example.com", "password", "passw0rd1",
                                "firstName", "C", "lastName", "N"))))
                .andExpect(status().isCreated())
                .andReturn();
        long userId = objectMapper.readTree(reg.getResponse().getContentAsString())
                .get("user").get("id").asLong();

        // admin forces email verification at next login
        mockMvc.perform(patch(org + "/users/" + userId)
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("requireEmailVerificationAtNextLogin", true))))
                .andExpect(status().isOk());

        MvcResult login = mockMvc.perform(post(orgAuth + "/login")
                        .header("X-Client-Id", clientKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("organisationId", orgId, "identifier", "chnext",
                                "password", "passw0rd1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.challenge.purpose").value(VerificationPurpose.EMAIL_VERIFICATION.name()))
                .andExpect(jsonPath("$.challenge.channel").value(VerificationChannel.EMAIL.name()))
                .andReturn();

        String challengeToken = objectMapper.readTree(login.getResponse().getContentAsString())
                .get("challenge").get("challengeToken").asText();
        String code = capturedCode();

        MvcResult solved = mockMvc.perform(post(orgAuth + "/challenges/verify")
                        .header("X-Client-Id", clientKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("challengeToken", challengeToken, "code", code))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.emailVerified").value(true))
                .andReturn();
        String token = objectMapper.readTree(solved.getResponse().getContentAsString()).get("accessToken").asText();

        // a subsequent login is a plain password login again (flag consumed)
        mockMvc.perform(post(orgAuth + "/login")
                        .header("X-Client-Id", clientKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("organisationId", orgId, "identifier", "chnext",
                                "password", "passw0rd1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.challenge").doesNotExist());

        mockMvc.perform(get(org + "/users/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
    }

    @Test
    void registerRequiredVerificationGatesSessionUntilEmailVerified() throws Exception {
        String platform = "/" + SLUGS[2];
        String boss = registerPlatform("ch-gate-boss@nexx.io", SLUGS[2]);
        long orgId = createOrganisation(boss, platform, "GateVerification Org", "gateverification-org");
        String org = platform + "/organisations/" + orgId;
        String orgAuth = platform + "/auth";
        String clientKey = createClient(boss, org, "Test Client");
        enable(platform, orgId, boss, Map.of(
                "requireEmailVerificationOnRegister", true,
                "verificationMode", "OTP"));

        // the org demands verification: the code is sent at registration and
        // the session is gated (no refresh token) until the email is proven
        mockMvc.perform(post(orgAuth + "/register")
                        .header("X-Client-Id", clientKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "organisationId", orgId, "username", "chgate",
                                "email", "chgate@example.com", "password", "passw0rd1",
                                "firstName", "C", "lastName", "G"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.user.emailVerified").value(false))
                .andExpect(jsonPath("$.actions[0]").value("VERIFY_EMAIL"));

        String code = capturedCode();
        mockMvc.perform(post(orgAuth + "/verifications/verify")
                        .header("X-Client-Id", clientKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "organisationId", orgId, "identifier", "chgate@example.com",
                                "purpose", "EMAIL_VERIFICATION", "code", code))))
                .andExpect(status().isNoContent());

        // the next login is fully allowed
        mockMvc.perform(post(orgAuth + "/login")
                        .header("X-Client-Id", clientKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("organisationId", orgId, "identifier", "chgate",
                                "password", "passw0rd1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.user.emailVerified").value(true))
                .andExpect(jsonPath("$.actions").isEmpty());
    }

    @Test
    void twoFactorLoginIsLockedWhenNotifierNotConfigured() throws Exception {
        // Without a notification service the 2FA code cannot be sent, so the
        // login is rejected with a clear error instead of a send failure.
        when(nexxbotifyClient.isConfigured()).thenReturn(false);
        String platform = "/" + SLUGS[5];
        String boss = registerPlatform("ch-lock-boss@nexx.io", SLUGS[5]);
        long orgId = createOrganisation(boss, platform, "Locked 2fa Org", "locked2fa-org");
        String org = platform + "/organisations/" + orgId;
        String orgAuth = platform + "/auth";
        String clientKey = createClient(boss, org, "Test Client");
        enable(platform, orgId, boss, Map.of("twoFactorEnabled", true));
        registerUser(orgAuth, clientKey, orgId, "chlock", "chlock@example.com", "passw0rd1");

        mockMvc.perform(post(orgAuth + "/login")
                        .header("X-Client-Id", clientKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("organisationId", orgId, "identifier", "chlock",
                                "password", "passw0rd1"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void verificationModeDefaultDrivesDeliveryAndRequestCanOverride() throws Exception {
        String platform = "/" + SLUGS[3];
        String boss = registerPlatform("ch-mode-boss@nexx.io", SLUGS[3]);
        long orgId = createOrganisation(boss, platform, "Mode Org", "mode-org");
        String org = platform + "/organisations/" + orgId;
        String orgAuth = platform + "/auth";
        String clientKey = createClient(boss, org, "Test Client");
        enable(platform, orgId, boss, Map.of(
                "emailVerificationEnabled", true,
                "verificationMode", "LINK"));

        registerUser(orgAuth, clientKey, orgId, "chmode", "mode@example.com", "passw0rd1");

        // no delivery on the request: the org's LINK mode applies
        mockMvc.perform(post(orgAuth + "/verifications/request")
                        .header("X-Client-Id", clientKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "organisationId", orgId, "identifier", "mode@example.com",
                                "channel", "EMAIL", "purpose", "EMAIL_VERIFICATION"))))
                .andExpect(status().isOk());

        String link = capturedLink();
        mockMvc.perform(get(platform + "/auth/verifications/complete?token=" + extractToken(link)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(true));

        // an explicit delivery on the request still overrides the org default
        clearInvocations(nexxbotifyClient);
        mockMvc.perform(post(orgAuth + "/verifications/request")
                        .header("X-Client-Id", clientKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "organisationId", orgId, "identifier", "mode@example.com",
                                "channel", "EMAIL", "purpose", "EMAIL_VERIFICATION", "delivery", "OTP"))))
                .andExpect(status().isOk());
        capturedCode();
    }

    // --- helpers ---

    private void registerUser(String orgAuth, String clientKey, long orgId,
                              String username, String email, String password) throws Exception {
        mockMvc.perform(post(orgAuth + "/register")
                        .header("X-Client-Id", clientKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "organisationId", orgId, "username", username,
                                "email", email, "password", password,
                                "firstName", "C", "lastName", "U"))))
                .andExpect(status().isCreated());
    }

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
        verify(nexxbotifyClient).sendForOrganisation(any(), org.mockito.ArgumentMatchers.eq(VerificationDelivery.OTP),
                org.mockito.ArgumentMatchers.any(VerificationChannel.class),
                org.mockito.ArgumentMatchers.anyString(), variables.capture());
        return (String) variables.getValue().get("code");
    }

    @SuppressWarnings("unchecked")
    private String capturedLink() {
        ArgumentCaptor<Map<String, Object>> variables = ArgumentCaptor.forClass(Map.class);
        verify(nexxbotifyClient).sendForOrganisation(any(), org.mockito.ArgumentMatchers.eq(VerificationDelivery.LINK),
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
                                "platformName", "Challenge Platform", "platformSlug", slug))))
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