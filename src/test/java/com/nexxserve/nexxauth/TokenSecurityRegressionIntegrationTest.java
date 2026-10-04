package com.nexxserve.nexxauth;

import com.nexxserve.nexxauth.entity.LogEntry;
import com.nexxserve.nexxauth.entity.OrganisationRefreshToken;
import com.nexxserve.nexxauth.entity.OrganisationVerificationToken;
import com.nexxserve.nexxauth.entity.VerificationChannel;
import com.nexxserve.nexxauth.entity.VerificationPurpose;
import com.nexxserve.nexxauth.repository.LogEntryRepository;
import com.nexxserve.nexxauth.repository.OrganisationClientRepository;
import com.nexxserve.nexxauth.repository.OrganisationRefreshTokenRepository;
import com.nexxserve.nexxauth.repository.OrganisationVerificationTokenRepository;
import com.nexxserve.nexxauth.util.RefreshTokens;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression guards for the token/session security fixes:
 *
 * <ul>
 *   <li>H1 - a rotated org refresh token inherits the session context
 *       (session_id / client_key / ip / user-agent / hostname) of its parent,
 *       so sessions stay trackable and revocable across rotations.</li>
 *   <li>H3 - a failed login's security audit row survives the rollback of the
 *       rejected request (persisted, not just console-logged).</li>
 *   <li>M1 - a client created with maxSessionsPerUser below 1 is rejected, and a
 *       bad stored value can never crash login with a 500.</li>
 *   <li>M2 - the nightly verification-token purge actually runs (deleteExpired
 *       has a transaction of its own).</li>
 *   <li>M3 - disabling a client invalidates the X-Client-Id cache immediately,
 *       so the credential stops authenticating without waiting for the TTL.</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
class TokenSecurityRegressionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrganisationRefreshTokenRepository refreshTokenRepository;

    @Autowired
    private OrganisationClientRepository clientRepository;

    @Autowired
    private OrganisationVerificationTokenRepository verificationTokenRepository;

    @Autowired
    private com.nexxserve.nexxauth.repository.OrganisationRepository organisationRepository;

    @Autowired
    private LogEntryRepository logEntryRepository;

    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    // --- H1: rotation preserves session context -----------------------------

    @Test
    void rotatedTokenInheritsSessionContext() throws Exception {
        String platform = "/sec-h1";
        String boss = registerPlatform("sec-h1-boss@nexx.io", "sec-h1");
        long orgId = createOrganisation(boss, platform, "H1 Org", "h1-org");
        String clientKey = createClient(boss, platform + "/organisations/" + orgId, "H1 Client");
        String orgAuth = platform + "/auth";

        registerOrgUser(orgAuth, clientKey, "h1user", "pass1234");
        OrgTokens login = loginOrg(orgAuth, clientKey, "h1user", "pass1234");
        OrganisationRefreshToken original = tokenByHash(login.refreshToken());
        assertNotNull(original.getSessionId(), "login should mint a session id");
        assertNotNull(original.getClientKey(), "login should record the client key");

        MvcResult refreshed = mockMvc.perform(post(orgAuth + "/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", login.refreshToken()))))
                .andExpect(status().isOk())
                .andReturn();
        String rotatedRaw = objectMapper.readTree(refreshed.getResponse().getContentAsString())
                .get("refreshToken").asText();

        OrganisationRefreshToken rotated = tokenByHash(rotatedRaw);
        assertEquals(original.getSessionId(), rotated.getSessionId(),
                "rotated token must inherit session_id");
        assertEquals(original.getClientKey(), rotated.getClientKey(),
                "rotated token must inherit client_key (per-client TTL overrides)");
        assertEquals(original.getIpAddress(), rotated.getIpAddress(),
                "rotated token must inherit ip_address");
        assertEquals(original.getUserAgent(), rotated.getUserAgent(),
                "rotated token must inherit user_agent");
        assertEquals(original.getHostname(), rotated.getHostname(),
                "rotated token must inherit hostname");
        assertTrue(original.isRevoked() || tokenByHash(login.refreshToken()).isRevoked(),
                "the parent token must be revoked on rotation (re-read from DB)");
    }

    // --- H3: failed-login audit survives rollback ---------------------------

    @Test
    void failedPlatformLoginIsPersistedToLogEntries() throws Exception {
        String boss = registerPlatform("sec-h3-boss@nexx.io", "sec-h3");
        // sanity: the boss exists
        assertNotNull(boss);

        long before = countLogEntries();
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "sec-h3-boss@nexx.io", "password", "wrong-password"))))
                .andExpect(status().isUnauthorized());

        List<LogEntry> entries = logEntryRepository.findAll();
        assertTrue(entries.size() > before,
                "a rejected login must persist an audit row that survives rollback");
        assertTrue(entries.stream().anyMatch(e ->
                        e.getEventType() != null && e.getEventType().contains("LOGIN_FAILURE")),
                "expected a LOGIN_FAILURE audit row, found: "
                        + entries.stream().map(LogEntry::getEventType).toList());
    }

    // --- M1: session-limit arithmetic + validation --------------------------

    @Test
    void clientCreatedWithNonPositiveSessionLimitIsRejected() throws Exception {
        String platform = "/sec-m1v";
        String boss = registerPlatform("sec-m1v-boss@nexx.io", "sec-m1v");
        long orgId = createOrganisation(boss, platform, "M1V Org", "m1v-org");
        String clientsPath = platform + "/organisations/" + orgId + "/clients";

        mockMvc.perform(post(clientsPath)
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Zero", "type", "WEB", "maxSessionsPerUser", 0))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post(clientsPath)
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Negative", "type", "WEB", "maxSessionsPerUser", -5))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void badStoredSessionLimitDoesNotBreakLogin() throws Exception {
        String platform = "/sec-m1c";
        String boss = registerPlatform("sec-m1c-boss@nexx.io", "sec-m1c");
        long orgId = createOrganisation(boss, platform, "M1C Org", "m1c-org");
        String clientKey = createClient(boss, platform + "/organisations/" + orgId, "M1C Client");
        String orgAuth = platform + "/auth";

        // Simulate a legacy/bad row that slipped past validation: force the
        // stored session limit to 0. Login must clamp (>=1) instead of throwing
        // IndexOutOfBoundsException -> generic 500.
        var client = clientRepository.findByClientKey(clientKey).orElseThrow();
        client.setMaxSessionsPerUser(0);
        clientRepository.saveAndFlush(client);

        // register, then login with the bad limit in place: must succeed, not 500
        registerOrgUser(orgAuth, clientKey, "m1cuser", "pass1234");
        mockMvc.perform(post(orgAuth + "/login")
                        .header("X-Client-Id", clientKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("identifier", "m1cuser", "identifierType", "USERNAME", "password", "pass1234"))))
                .andExpect(status().isOk());
    }

    // --- M2: verification-token purge runs ----------------------------------

    @Test
    void expiredVerificationTokensArePurged() throws Exception {
        String platform = "/sec-m2";
        String boss = registerPlatform("sec-m2-boss@nexx.io", "sec-m2");
        long orgId = createOrganisation(boss, platform, "M2 Org", "m2-org");

        // Build an expired verification token directly and purge it.
        OrganisationVerificationToken expired = new OrganisationVerificationToken();
        expired.setOrganisation(organisationRepository.findById(orgId).orElseThrow());
        expired.setPurpose(VerificationPurpose.EMAIL_VERIFICATION);
        expired.setChannel(VerificationChannel.EMAIL);
        expired.setDelivery(com.nexxserve.nexxauth.entity.VerificationDelivery.OTP);
        expired.setIdentifier("m2user@nexx.io");
        expired.setTokenHash(RefreshTokens.hash("raw"));
        expired.setExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS));
        expired.setSentAt(Instant.now().minus(1, ChronoUnit.DAYS));
        verificationTokenRepository.saveAndFlush(expired);
        assertNotNull(expired.getId());

        verificationTokenRepository.deleteExpired(Instant.now());

        assertTrue(verificationTokenRepository.findById(expired.getId()).isEmpty(),
                "expired verification token should be deleted by the purge");
    }

    // --- M3: client cache invalidation ---------------------------------------

    @Test
    void disablingClientStopsItAuthenticatingImmediately() throws Exception {
        String platform = "/sec-m3";
        String boss = registerPlatform("sec-m3-boss@nexx.io", "sec-m3");
        long orgId = createOrganisation(boss, platform, "M3 Org", "m3-org");
        String clientKey = createClient(boss, platform + "/organisations/" + orgId, "M3 Client");

        // warm the cache with a successful client-authenticated request
        String orgAuth = platform + "/auth";
        registerOrgUser(orgAuth, clientKey, "m3user", "pass1234");
        mockMvc.perform(post(orgAuth + "/login")
                        .header("X-Client-Id", clientKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("identifier", "m3user", "identifierType", "USERNAME", "password", "pass1234"))))
                .andExpect(status().isOk());

        // disable the client
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch(platform + "/organisations/" + orgId + "/clients/" + clientKey)
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("enabled", false))))
                .andExpect(status().isOk());

        // immediately after disabling, the client must be rejected (403), not
        // still served from the 30s cache
        mockMvc.perform(post(orgAuth + "/login")
                        .header("X-Client-Id", clientKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("identifier", "m3user", "identifierType", "USERNAME", "password", "pass1234"))))
                .andExpect(status().isForbidden());
    }

    // --- helpers ------------------------------------------------------------

    private long countLogEntries() {
        return logEntryRepository.count();
    }

    private OrganisationRefreshToken tokenByHash(String rawToken) {
        // findByTokenHash now takes a pessimistic row lock, which requires an
        // active transaction - read it through one.
        OrganisationRefreshToken[] holder = new OrganisationRefreshToken[1];
        new org.springframework.transaction.support.TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> holder[0] =
                        refreshTokenRepository.findByTokenHash(RefreshTokens.hash(rawToken)).orElseThrow());
        return holder[0];
    }

    private record OrgTokens(String accessToken, String refreshToken) {
    }

    private String registerPlatform(String email, String slug) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "firstName", "F", "lastName", "L",
                                "email", email, "password", "password1",
                                "platformName", "Security Regression Platform", "platformSlug", slug))))
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

    private void registerOrgUser(String orgAuth, String clientKey, String identifier, String password)
            throws Exception {
        mockMvc.perform(post(orgAuth + "/register")
                        .header("X-Client-Id", clientKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "username", identifier, "password", password,
                                "firstName", "F", "lastName", "L"))))
                .andExpect(status().isCreated());
    }

    private OrgTokens loginOrg(String orgAuth, String clientKey, String identifier, String password)
            throws Exception {
        MvcResult result = mockMvc.perform(post(orgAuth + "/login")
                        .header("X-Client-Id", clientKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("identifier", identifier, "identifierType", "USERNAME", "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return new OrgTokens(node.get("accessToken").asText(), node.get("refreshToken").asText());
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}