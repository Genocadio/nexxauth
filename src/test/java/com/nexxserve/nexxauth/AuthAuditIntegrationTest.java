package com.nexxserve.nexxauth;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.nexxserve.nexxauth.service.AuthAuditService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the AUDIT log trail: every security-relevant auth event (register,
 * login success/failure, refresh, logout, key rotation) is written to the
 * dedicated {@code AUDIT} logger with the actor and organisation. In prod the
 * ECS structured format ships these as JSON; here we capture the logback
 * events and assert the event names.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthAuditIntegrationTest {

    private static final String[] SLUGS = {"audit1", "audit2", "audit3"};

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attachAppender() {
        Logger auditLogger = (Logger) LoggerFactory.getLogger(AuthAuditService.LOGGER_NAME);
        appender = new ListAppender<>();
        appender.start();
        auditLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        Logger auditLogger = (Logger) LoggerFactory.getLogger(AuthAuditService.LOGGER_NAME);
        auditLogger.detachAppender(appender);
    }

    @Test
    void platformAuthEmitsAuditEvents() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "firstName", "A", "lastName", "B",
                                "email", "audit-boss@nexx.io", "password", "password1",
                                "platformName", "Audit Platform", "platformSlug", SLUGS[0]))))
                .andExpect(status().isCreated());

        // failed then successful login
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "audit-boss@nexx.io", "password", "wrong"))))
                .andExpect(status().isUnauthorized());
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "audit-boss@nexx.io", "password", "password1"))))
                .andExpect(status().isOk())
                .andReturn();
        String refresh = objectMapper.readTree(login.getResponse().getContentAsString()).get("refreshToken").asText();

        // rotate (the original token is revoked by the rotation), then log out
        // with the fresh token so the actor resolves for the audit event
        MvcResult rotated = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", refresh))))
                .andExpect(status().isOk())
                .andReturn();
        String refresh2 = objectMapper.readTree(rotated.getResponse().getContentAsString()).get("refreshToken").asText();
        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", refresh2))))
                .andExpect(status().isNoContent());

        List<String> events = messages();
        assertThat(events).anyMatch(m -> m.contains("event=" + AuthAuditService.PLATFORM_REGISTER));
        assertThat(events).anyMatch(m -> m.contains("event=" + AuthAuditService.PLATFORM_LOGIN_FAILURE));
        assertThat(events).anyMatch(m -> m.contains("event=" + AuthAuditService.PLATFORM_LOGIN_SUCCESS));
        assertThat(events).anyMatch(m -> m.contains("event=" + AuthAuditService.PLATFORM_REFRESH));
        assertThat(events).anyMatch(m -> m.contains("event=" + AuthAuditService.PLATFORM_LOGOUT));
        // the logout event names the actor
        assertThat(events).anyMatch(m -> m.contains("event=" + AuthAuditService.PLATFORM_LOGOUT)
                && m.contains("actor=audit-boss@nexx.io"));
    }

    @Test
    void orgAuthEmitsAuditEventsIncludingKeyRotation() throws Exception {
        String platform = "/" + SLUGS[1];
        String boss = registerPlatform("audit-org-boss@nexx.io", SLUGS[1]);

        MvcResult org = mockMvc.perform(post(platform + "/organisations")
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Audit Org", "slug", "audit-org"))))
                .andExpect(status().isCreated())
                .andReturn();
        long orgId = objectMapper.readTree(org.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post(platform + "/organisations/audit-org/user-fields")
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("key", "badge", "label", "Badge", "fieldType", "STRING"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post(platform + "/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "organisationId", orgId, "identifier", "gary",
                                "password", "orgpass1", "firstName", "G", "lastName", "R"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post(platform + "/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("organisationId", orgId, "identifier", "gary", "password", "wrong"))))
                .andExpect(status().isUnauthorized());
        MvcResult login = mockMvc.perform(post(platform + "/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("organisationId", orgId, "identifier", "gary", "password", "orgpass1"))))
                .andExpect(status().isOk())
                .andReturn();
        String refresh = objectMapper.readTree(login.getResponse().getContentAsString()).get("refreshToken").asText();

        mockMvc.perform(post(platform + "/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", refresh))))
                .andExpect(status().isOk());
        mockMvc.perform(post(platform + "/organisations/audit-org/keys/rotate")
                        .header("Authorization", bearer(boss)))
                .andExpect(status().isOk());

        List<String> events = messages();
        assertThat(events).anyMatch(m -> m.contains("event=" + AuthAuditService.ORG_REGISTER));
        assertThat(events).anyMatch(m -> m.contains("event=" + AuthAuditService.ORG_LOGIN_FAILURE));
        assertThat(events).anyMatch(m -> m.contains("event=" + AuthAuditService.ORG_LOGIN_SUCCESS));
        assertThat(events).anyMatch(m -> m.contains("event=" + AuthAuditService.ORG_REFRESH));
        assertThat(events).anyMatch(m -> m.contains("event=" + AuthAuditService.ORG_KEY_ROTATED)
                && m.contains("organisation=audit-org"));
        // org auth events name the organisation
        assertThat(events).anyMatch(m -> m.contains("event=" + AuthAuditService.ORG_LOGIN_SUCCESS)
                && m.contains("actor=gary") && m.contains("organisation=audit-org"));
        // user-field config changes are audited too
        assertThat(events).anyMatch(m -> m.contains("event=" + AuthAuditService.ORG_USER_FIELD_CREATED)
                && m.contains("organisation=audit-org") && m.contains("detail=badge"));
    }

    @Test
    void refreshReuseIsAuditedAsTokenTheft() throws Exception {
        String platform = "/" + SLUGS[2];
        String orgSlug = "reuse-org";
        String org = platform + "/organisations/" + orgSlug;
        String boss = registerPlatform("audit-reuse-boss@nexx.io", SLUGS[2]);
        mockMvc.perform(post(platform + "/organisations")
                        .header("Authorization", bearer(boss))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Reuse Org", "slug", orgSlug))))
                .andExpect(status().isCreated());
        long orgId = getOrgId(boss, org);

        mockMvc.perform(post(platform + "/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "organisationId", orgId, "identifier", "heidi",
                                "password", "orgpass1", "firstName", "H", "lastName", "I"))))
                .andExpect(status().isCreated());
        MvcResult login = mockMvc.perform(post(platform + "/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("organisationId", orgId, "identifier", "heidi", "password", "orgpass1"))))
                .andExpect(status().isOk())
                .andReturn();
        String refresh1 = objectMapper.readTree(login.getResponse().getContentAsString()).get("refreshToken").asText();

        // rotate, then present the rotated (now revoked) token again
        mockMvc.perform(post(platform + "/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", refresh1))))
                .andExpect(status().isOk());
        mockMvc.perform(post(platform + "/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", refresh1))))
                .andExpect(status().isUnauthorized());

        assertThat(messages()).anyMatch(m -> m.contains("event=" + AuthAuditService.ORG_TOKEN_REUSE)
                && m.contains("actor=heidi") && m.contains("organisation=" + orgSlug));
    }

    // --- helpers ---

    private List<String> messages() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    private String registerPlatform(String email, String slug) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "firstName", "F", "lastName", "L",
                                "email", email, "password", "password1",
                                "platformName", "Audit Platform", "platformSlug", slug))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private long getOrgId(String boss, String org) throws Exception {
        MvcResult result = mockMvc.perform(get(org).header("Authorization", bearer(boss)))
                .andExpect(status().isOk())
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
