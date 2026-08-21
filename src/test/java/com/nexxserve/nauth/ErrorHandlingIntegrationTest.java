package com.nexxserve.nauth;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ErrorHandlingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void unknownRouteReturns404ForAuthenticatedAnd401ForAnonymous() throws Exception {
        // Anonymous probes are rejected before routing: everything requires auth,
        // so unknown routes don't leak endpoint existence.
        MvcResult anonymous = mockMvc.perform(get("/nope/does-not-exist"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("X-Request-Id"))
                .andReturn();
        assertErrorShape(objectMapper.readTree(anonymous.getResponse().getContentAsString()), 401);

        // An authenticated caller hitting a nonexistent path gets a proper 404.
        MvcResult registered = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "firstName", "A",
                                "lastName", "B",
                                "email", "routing@example.com",
                                "password", "password1",
                                "platformName", "Routing Inc"))))
                .andExpect(status().isCreated())
                .andReturn();
        String accessToken = objectMapper.readTree(registered.getResponse().getContentAsString())
                .get("accessToken").asText();

        MvcResult result = mockMvc.perform(get("/nope/does-not-exist")
                        .header("Authorization", bearer(accessToken)))
                .andExpect(status().isNotFound())
                .andExpect(header().exists("X-Request-Id"))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertErrorShape(body, 404);
        assertThat(body.get("path").asText()).isEqualTo("/nope/does-not-exist");
        assertThat(body.get("requestId").asText()).isEqualTo(result.getResponse().getHeader("X-Request-Id"));
    }

    @Test
    void missingRequiredFieldsReturn400WithFieldErrors() throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Only\"}"))
                .andExpect(status().isBadRequest())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertErrorShape(body, 400);
        assertThat(body.get("fieldErrors")).isNotNull();
        assertThat(body.get("fieldErrors").size()).isGreaterThanOrEqualTo(1);
        assertThat(body.get("fieldErrors").get(0).get("field").asText()).isNotBlank();
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not valid json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed or missing request body"));
    }

    @Test
    void badCredentialsReturn401WithStructuredBody() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "firstName", "A",
                                "lastName", "B",
                                "email", "errors@example.com",
                                "password", "password1",
                                "platformName", "Errors Inc"))))
                .andExpect(status().isCreated());

        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "errors@example.com", "password", "wrong"))))
                .andExpect(status().isUnauthorized())
                .andReturn();

        assertErrorShape(objectMapper.readTree(result.getResponse().getContentAsString()), 401);
    }

    @Test
    void missingOrInvalidTokenReturns401WithStructuredBody() throws Exception {
        MvcResult missing = mockMvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized())
                .andReturn();
        assertErrorShape(objectMapper.readTree(missing.getResponse().getContentAsString()), 401);

        MvcResult invalid = mockMvc.perform(get("/auth/me").header("Authorization", "Bearer garbage"))
                .andExpect(status().isUnauthorized())
                .andReturn();
        assertErrorShape(objectMapper.readTree(invalid.getResponse().getContentAsString()), 401);
    }

    @Test
    void duplicateEmailReturns409() throws Exception {
        String body = json(Map.of(
                "firstName", "A",
                "lastName", "B",
                "email", "dup-errors@example.com",
                "password", "password1",
                "platformName", "Dup Errors"));

        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        MvcResult result = mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andReturn();

        assertErrorShape(objectMapper.readTree(result.getResponse().getContentAsString()), 409);
    }

    @Test
    void unsupportedMediaTypeReturns415() throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("hello"))
                .andExpect(status().isUnsupportedMediaType())
                .andReturn();

        assertErrorShape(objectMapper.readTree(result.getResponse().getContentAsString()), 415);
    }

    @Test
    void wrongMethodReturns405() throws Exception {
        MvcResult result = mockMvc.perform(get("/auth/login"))
                .andExpect(status().isMethodNotAllowed())
                .andReturn();

        assertErrorShape(objectMapper.readTree(result.getResponse().getContentAsString()), 405);
    }

    private void assertErrorShape(JsonNode body, int status) {
        assertThat(body.get("timestamp").asText()).isNotBlank();
        assertThat(body.get("status").asInt()).isEqualTo(status);
        assertThat(body.get("error").asText()).isNotBlank();
        assertThat(body.get("message").asText()).isNotBlank();
        assertThat(body.get("path").asText()).isNotBlank();
        assertThat(body.get("requestId").asText()).isNotBlank();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
