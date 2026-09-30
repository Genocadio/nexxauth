package com.nexxserve.nexxauth.service;

import com.nexxserve.nexxauth.entity.VerificationChannel;
import com.nexxserve.nexxauth.entity.VerificationDelivery;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class NexxbotifyClient {

    private static final Logger log = LoggerFactory.getLogger(NexxbotifyClient.class);

    private final RestClient restClient;
    private final NexxbotifyProperties properties;
    private final ObjectMapper objectMapper;
    private final NexxbotifyTokenSigner tokenSigner;

    public NexxbotifyClient(RestClient.Builder builder, NexxbotifyProperties properties) {
        this(builder, properties, null, null);
    }

    public NexxbotifyClient(RestClient.Builder builder, NexxbotifyProperties properties,
                            ObjectMapper objectMapper) {
        this(builder, properties, objectMapper, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public NexxbotifyClient(RestClient.Builder builder, NexxbotifyProperties properties,
                            @org.springframework.beans.factory.annotation.Autowired(required = false) ObjectMapper objectMapper,
                            @org.springframework.beans.factory.annotation.Autowired(required = false) NexxbotifyTokenSigner tokenSigner) {
        this.properties = properties;
        this.tokenSigner = tokenSigner;
        this.objectMapper = objectMapper != null ? objectMapper : JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeoutMs());
        requestFactory.setReadTimeout(properties.getReadTimeoutMs());
        RestClient.Builder configured = builder
                .requestFactory(requestFactory)
                .defaultHeaders(headers -> {
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.ALL));
                });
        if (configured == null) {
            configured = builder;
        }
        try {
            var withInterceptor = configured.requestInterceptor((request, body, execution) -> {
                // Generate a fresh, single-use signed token for each individual request
                if (this.tokenSigner != null && this.tokenSigner.isConfigured()) {
                    String token = this.tokenSigner.generateToken();
                    if (token != null) {
                        request.getHeaders().setBearerAuth(token);
                    }
                }
                if (properties.getApiKey() != null && !properties.getApiKey().isBlank()) {
                    request.getHeaders().set("X-Api-Key", properties.getApiKey());
                }
                return execution.execute(request, body);
            });
            if (withInterceptor != null) {
                configured = withInterceptor;
            }
        } catch (Exception ignored) {
        }

        if (properties.getBaseUrl() != null && !properties.getBaseUrl().isBlank()) {
            var withBase = configured.baseUrl(properties.getBaseUrl());
            if (withBase != null) {
                configured = withBase;
            }
        }
        this.restClient = configured.build();
    }

    /** Whether a nexxbotify base URL is configured. When {@code false}, the
     * features that deliver codes/links through this client are unavailable. */
    public boolean isConfigured() {
        return properties.getBaseUrl() != null && !properties.getBaseUrl().isBlank();
    }

    /**
     * Performs a health check against nexxnotify's {@code GET /healthz} endpoint.
     * Returns {@code true} if the service responds with HTTP 200, {@code false} otherwise.
     * The health endpoint is public (no API key required) so this always works.
     */
    public boolean checkHealth() {
        if (!isConfigured()) return false;
        try {
            var response = restClient.get()
                    .uri("/healthz")
                    .retrieve()
                    .toBodilessEntity();
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.warn("nexxnotify health check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Deterministic flow ID for an organisation in nexxnotify.
     */
    public static String organisationFlowId(com.nexxserve.nexxauth.entity.Organisation organisation) {
        return "org_" + organisation.getId() + "_auth";
    }

    /**
     * Provisions or updates the dedicated flow in nexxnotify for an organisation.
     * The flow includes email delivery by default, plus SMS delivery if {@code includeSms}
     * is true (e.g. phone login or phone verification enabled).
     */
    public void ensureOrganisationFlow(com.nexxserve.nexxauth.entity.Organisation organisation, boolean includeSms) {
        if (!isConfigured() || organisation == null || organisation.getId() == null) {
            return;
        }
        String flowId = organisationFlowId(organisation);
        String orgName = organisation.getName() != null && !organisation.getName().isBlank()
                ? organisation.getName()
                : organisation.getSlug();

        List<CreateChannelRequest> channels = new java.util.ArrayList<>();
        channels.add(new CreateChannelRequest(
                "email",
                true,
                false,
                null,
                null,
                Map.of(
                        "subject", "Your " + orgName + " verification code",
                        "body", "Your " + orgName + " verification code is {{code}}.",
                        "html", "<p>Your " + orgName + " verification code is <strong>{{code}}</strong>.</p>"
                ),
                List.of(),
                Map.of()
        ));

        if (includeSms) {
            channels.add(new CreateChannelRequest(
                    "sms",
                    true,
                    false,
                    null,
                    null,
                    Map.of(
                            "body", "Your " + orgName + " verification code is {{code}}."
                    ),
                    List.of(),
                    Map.of()
            ));
        }

        CreateFlowRequest request = new CreateFlowRequest(
                flowId,
                orgName + " Auth",
                true,
                new InputContract(
                        false,
                        true,
                        Map.of(
                                "code", Map.of("type", "string", "required", false),
                                "link", Map.of("type", "string", "required", false)
                        )
                ),
                channels
        );

        try {
            restClient.post()
                    .uri("/flows")
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Provisioned nexxnotify flow {} for organisation {}", flowId, organisation.getSlug());
        } catch (org.springframework.web.client.HttpClientErrorException.Conflict conflict) {
            log.debug("nexxnotify flow {} already exists for org {}", flowId, organisation.getSlug());
            if (includeSms) {
                try {
                    restClient.post()
                            .uri("/flows/{id}/channels", flowId)
                            .body(new CreateChannelRequest(
                                    "sms",
                                    true,
                                    false,
                                    null,
                                    null,
                                    Map.of("body", "Your " + orgName + " verification code is {{code}}."),
                                    List.of(),
                                    Map.of()
                            ))
                            .retrieve()
                            .toBodilessEntity();
                } catch (Exception e) {
                    log.debug("Could not upsert SMS channel for flow {}: {}", flowId, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to provision nexxnotify flow {} for org {}: {}", flowId, organisation.getSlug(), e.getMessage());
        }
    }

    /**
     * Sends a delivery for an organisation's user. Uses the organisation's dedicated
     * flow if available, auto-provisioning the flow on-demand if it does not yet exist.
     */
    public void sendForOrganisation(com.nexxserve.nexxauth.entity.Organisation organisation,
                                   VerificationDelivery delivery, VerificationChannel channel,
                                   String identifier, Map<String, Object> variables) {
        if (!isConfigured()) {
            throw new IllegalStateException(
                    "The verification service is not configured; this feature is unavailable");
        }
        String flowId = organisation != null && organisation.getId() != null
                ? organisationFlowId(organisation)
                : properties.flowIdFor(delivery, channel);

        SendRequest body = new SendRequest(flowId, java.util.UUID.randomUUID().toString(),
                variables, receiverFor(channel, identifier));
        try {
            executeSend(body, flowId, identifier);
        } catch (RestClientResponseException notFound) {
            if (notFound.getStatusCode().value() == 404 && organisation != null) {
                log.info("Flow {} not found for org {}; auto-provisioning and retrying send", flowId, organisation.getSlug());
                ensureOrganisationFlow(organisation, organisation.isPhoneCanLogin());
                executeSend(body, flowId, identifier);
                return;
            }
            throw notFound;
        }
    }

    /**
     * Sends a delivery for {@code variables} to the receiver address using global flow IDs.
     *
     * @throws IllegalStateException when nexxbotify is unreachable, rejects the
     *                               request, or reports a failed delivery
     */
    public void send(VerificationDelivery delivery, VerificationChannel channel,
                     String identifier, Map<String, Object> variables) {
        if (!isConfigured()) {
            throw new IllegalStateException(
                    "The verification service is not configured; this feature is unavailable");
        }
        String flowId = properties.flowIdFor(delivery, channel);
        SendRequest body = new SendRequest(flowId, java.util.UUID.randomUUID().toString(),
                variables, receiverFor(channel, identifier));
        executeSend(body, flowId, identifier);
    }

    private void executeSend(SendRequest body, String flowId, String identifier) {
        try {
            String raw = restClient.post()
                    .uri("/send")
                    .accept(MediaType.APPLICATION_JSON, MediaType.ALL)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            if (raw == null || raw.isBlank()) {
                throw new IllegalStateException("nexxbotify returned an empty response");
            }
            SendResponse response = objectMapper.readValue(raw, SendResponse.class);
            if (response == null) {
                throw new IllegalStateException("nexxbotify returned an empty response");
            }
            // We always send exactly one receiver on one channel, so a send is
            // only delivered when nexxbotify reports at least one "sent" result.
            // An empty/null result list means no channel matched the receiver and
            // nothing was sent — that must surface as a failure, not a false success.
            boolean delivered = response.results != null
                    && !response.results.isEmpty()
                    && response.results.stream().anyMatch(r -> "sent".equals(r.status));
            if (!delivered) {
                log.warn("nexxbotify could not deliver flow {} to {}: status={} results={}",
                        flowId, identifier, response.status, response.results);
                throw new IllegalStateException("The verification could not be delivered. Try a different channel");
            }
            log.debug("nexxbotify delivered flow {} to {} (send id {})", flowId, identifier, response.id);
        } catch (IllegalStateException e) {
            throw e;
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                throw e;
            }
            log.warn("nexxbotify send HTTP {} for flow {} to {}: {}",
                    e.getStatusCode().value(), flowId, identifier, e.getResponseBodyAsString());
            throw new IllegalStateException("The verification service returned an error", e);
        } catch (Exception e) {
            log.warn("nexxbotify send failed for flow {} to {}: {}",
                    flowId, identifier, e.getMessage());
            throw new IllegalStateException("The verification service is temporarily unavailable", e);
        }
    }

    /**
     * Retrieves notification templates for an organisation from nexxnotify.
     * Returns default values if nexxnotify is unreachable or not yet configured.
     */
    public com.nexxserve.nexxauth.dto.response.OrganisationTemplatesResponse getOrganisationTemplates(
            com.nexxserve.nexxauth.entity.Organisation organisation) {
        if (organisation == null || organisation.getId() == null) {
            throw new IllegalArgumentException("Organisation is required");
        }
        String flowId = organisationFlowId(organisation);
        String orgName = organisation.getName() != null && !organisation.getName().isBlank()
                ? organisation.getName()
                : organisation.getSlug();

        String defaultEmailSubject = "Your " + orgName + " verification code";
        String defaultEmailBody = "Your " + orgName + " verification code is {{code}}.";
        String defaultEmailHtml = "<div style=\"font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 32px 24px; background-color: #ffffff; border-radius: 8px; border: 1px solid #e2e8f0;\">\n" +
                "  <h2 style=\"color: #0f172a; margin-top: 0; font-size: 20px; font-weight: 600;\">" + orgName + " Verification</h2>\n" +
                "  <p style=\"color: #475569; font-size: 15px; line-height: 1.5; margin-bottom: 24px;\">Use the verification code below to complete your sign-in or verification request:</p>\n" +
                "  <div style=\"background-color: #f1f5f9; border-radius: 6px; padding: 16px 24px; text-align: center; margin: 24px 0;\">\n" +
                "    <span style=\"font-family: monospace; font-size: 32px; font-weight: 700; letter-spacing: 6px; color: #0f172a;\">{{code}}</span>\n" +
                "  </div>\n" +
                "  <p style=\"color: #64748b; font-size: 13px; line-height: 1.5;\">This code will expire in 10 minutes. If you did not make this request, you can safely ignore this email.</p>\n" +
                "</div>";
        String defaultSmsBody = "Your " + orgName + " verification code is: {{code}}.";

        if (!isConfigured()) {
            return new com.nexxserve.nexxauth.dto.response.OrganisationTemplatesResponse(
                    flowId,
                    new com.nexxserve.nexxauth.dto.response.OrganisationTemplatesResponse.EmailTemplate(
                            true, defaultEmailSubject, defaultEmailBody, defaultEmailHtml),
                    new com.nexxserve.nexxauth.dto.response.OrganisationTemplatesResponse.SmsTemplate(
                            organisation.isPhoneCanLogin(), defaultSmsBody)
            );
        }

        try {
            String raw = restClient.get()
                    .uri("/flows/{id}", flowId)
                    .accept(MediaType.APPLICATION_JSON, MediaType.ALL)
                    .retrieve()
                    .body(String.class);

            FlowResponse flow = raw != null && !raw.isBlank()
                    ? objectMapper.readValue(raw, FlowResponse.class)
                    : null;

            if (flow != null && flow.channels() != null) {
                com.nexxserve.nexxauth.dto.response.OrganisationTemplatesResponse.EmailTemplate email = null;
                com.nexxserve.nexxauth.dto.response.OrganisationTemplatesResponse.SmsTemplate sms = null;

                for (FlowChannelResponse ch : flow.channels()) {
                    if ("email".equalsIgnoreCase(ch.channel())) {
                        String subj = ch.default_content() != null && ch.default_content().subject() != null
                                ? ch.default_content().subject()
                                : defaultEmailSubject;
                        String body = ch.default_content() != null && ch.default_content().body() != null
                                ? ch.default_content().body()
                                : defaultEmailBody;
                        String html = ch.default_content() != null && ch.default_content().html() != null
                                ? ch.default_content().html()
                                : defaultEmailHtml;
                        email = new com.nexxserve.nexxauth.dto.response.OrganisationTemplatesResponse.EmailTemplate(
                                ch.enabled() == null || ch.enabled(),
                                subj, body, html
                        );
                    } else if ("sms".equalsIgnoreCase(ch.channel())) {
                        String body = ch.default_content() != null && ch.default_content().body() != null
                                ? ch.default_content().body()
                                : defaultSmsBody;
                        sms = new com.nexxserve.nexxauth.dto.response.OrganisationTemplatesResponse.SmsTemplate(
                                ch.enabled() == null || ch.enabled(),
                                body
                        );
                    }
                }

                return new com.nexxserve.nexxauth.dto.response.OrganisationTemplatesResponse(
                        flowId,
                        email != null ? email : new com.nexxserve.nexxauth.dto.response.OrganisationTemplatesResponse.EmailTemplate(
                                true, defaultEmailSubject, defaultEmailBody, defaultEmailHtml),
                        sms != null ? sms : new com.nexxserve.nexxauth.dto.response.OrganisationTemplatesResponse.SmsTemplate(
                                organisation.isPhoneCanLogin(), defaultSmsBody)
                );
            }
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound e) {
            log.info("Flow {} not found; provisioning for org {}", flowId, organisation.getSlug());
            ensureOrganisationFlow(organisation, organisation.isPhoneCanLogin());
        } catch (Exception e) {
            log.warn("Failed to fetch templates from nexxnotify for flow {}: {}", flowId, e.getMessage());
        }

        return new com.nexxserve.nexxauth.dto.response.OrganisationTemplatesResponse(
                flowId,
                new com.nexxserve.nexxauth.dto.response.OrganisationTemplatesResponse.EmailTemplate(
                        true, defaultEmailSubject, defaultEmailBody, defaultEmailHtml),
                new com.nexxserve.nexxauth.dto.response.OrganisationTemplatesResponse.SmsTemplate(
                        organisation.isPhoneCanLogin(), defaultSmsBody)
        );
    }

    /**
     * Updates notification templates for an organisation in nexxnotify.
     */
    public com.nexxserve.nexxauth.dto.response.OrganisationTemplatesResponse updateOrganisationTemplates(
            com.nexxserve.nexxauth.entity.Organisation organisation,
            com.nexxserve.nexxauth.dto.request.UpdateOrganisationTemplatesRequest request) {
        if (!isConfigured()) {
            throw new IllegalStateException("The notification service is not configured");
        }
        if (organisation == null || organisation.getId() == null) {
            throw new IllegalArgumentException("Organisation is required");
        }

        ensureOrganisationFlow(organisation, true);
        String flowId = organisationFlowId(organisation);
        String orgName = organisation.getName() != null && !organisation.getName().isBlank()
                ? organisation.getName()
                : organisation.getSlug();

        if (request.emailSubject() != null || request.emailBody() != null || request.emailHtml() != null) {
            Map<String, Object> emailContent = new java.util.HashMap<>();
            if (request.emailSubject() != null) emailContent.put("subject", request.emailSubject());
            if (request.emailBody() != null) emailContent.put("body", request.emailBody());
            if (request.emailHtml() != null) emailContent.put("html", request.emailHtml());

            try {
                restClient.post()
                        .uri("/flows/{id}/channels", flowId)
                        .body(new CreateChannelRequest(
                                "email",
                                true,
                                false,
                                null,
                                null,
                                emailContent,
                                List.of(),
                                Map.of()
                        ))
                        .retrieve()
                        .toBodilessEntity();
                log.info("Updated email template for flow {}", flowId);
            } catch (Exception e) {
                log.warn("Failed to update email channel for flow {}: {}", flowId, e.getMessage());
                throw new IllegalStateException("Failed to update email template in notification service: " + e.getMessage(), e);
            }
        }

        if (request.smsBody() != null) {
            Map<String, Object> smsContent = new java.util.HashMap<>();
            smsContent.put("body", request.smsBody());

            try {
                restClient.post()
                        .uri("/flows/{id}/channels", flowId)
                        .body(new CreateChannelRequest(
                                "sms",
                                true,
                                false,
                                null,
                                null,
                                smsContent,
                                List.of(),
                                Map.of()
                        ))
                        .retrieve()
                        .toBodilessEntity();
                log.info("Updated SMS template for flow {}", flowId);
            } catch (Exception e) {
                log.warn("Failed to update SMS channel for flow {}: {}", flowId, e.getMessage());
                throw new IllegalStateException("Failed to update SMS template in notification service: " + e.getMessage(), e);
            }
        }

        return getOrganisationTemplates(organisation);
    }

    private List<Map<String, Object>> receiverFor(VerificationChannel channel, String identifier) {
        return switch (channel) {
            case EMAIL -> List.of(Map.of("email", identifier));
            case SMS -> List.of(Map.of("phone", identifier));
        };
    }

    /** Request body for creating a flow in nexxnotify. */
    public record CreateFlowRequest(String id, String name, boolean active,
                                    InputContract input, List<CreateChannelRequest> channels) {
    }

    public record InputContract(boolean allows_content, boolean allows_variables,
                                Map<String, Object> variables) {
    }

    public record CreateChannelRequest(String channel, Boolean enabled, Boolean uses_template,
                                       String template_name, List<String> template_param_order,
                                       Map<String, Object> default_content,
                                       List<String> required_variables,
                                       Map<String, Object> channel_config) {
    }

    /** Response structures for reading a flow from nexxnotify. */
    public record FlowMessageContent(String subject, String body, String html) {
    }

    public record FlowChannelResponse(String id, String channel, Boolean enabled,
                                      Boolean uses_template, String template_name,
                                      List<String> template_param_order,
                                      FlowMessageContent default_content,
                                      List<String> required_variables,
                                      Map<String, Object> channel_config) {
    }

    public record FlowResponse(String id, String name, Boolean active,
                               List<FlowChannelResponse> channels) {
    }

    /** Request body for {@code POST /send}. The {@code id} doubles as the
     * idempotency key in nexxbotify. */
    public record SendRequest(String flow_id, String id, Map<String, Object> variables,
                              List<Map<String, Object>> receivers) {
    }

    public record SendResult(String receiver, String channel, String status, String error) {
    }

    public record SendResponse(String id, String status, List<SendResult> results) {
    }
}