package com.nexxserve.nexxauth.service;

import com.nexxserve.nexxauth.entity.VerificationChannel;
import com.nexxserve.nexxauth.entity.VerificationDelivery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Thin HTTP client for the nexxbotify (notification) service. Sends a delivery
 * (OTP code or magic link) to an email address or phone number by calling
 * {@code POST /send} with the flow configured in {@link NexxbotifyProperties}.
 * <p>
 * nexxbotify resolves the channel from the receiver object: a receiver carrying
 * an {@code email} is delivered over the email channel, one carrying a
 * {@code phone} over the SMS channel. We only ever set the field matching the
 * requested {@link VerificationChannel}.
 * <p>
 * The call is best-effort guarded: timeouts are short and delivery failures are
 * surfaced as {@link IllegalStateException} so the caller returns a 502-style
 * error rather than a false success. A send is only considered delivered when
 * nexxbotify reports at least one result with status {@code sent}: an empty
 * result list (e.g. the flow has no enabled channel for the receiver) is a
 * failure, never a success.
 * <p>
 * When no base URL is configured the client is inert: {@link #isConfigured()}
 * returns {@code false} and {@link #send} fails fast, so dependent features
 * (verification, password reset, OTP/2FA delivery) lock cleanly instead of
 * attempting network calls.
 */
@Component
public class NexxbotifyClient {

    private static final Logger log = LoggerFactory.getLogger(NexxbotifyClient.class);

    private final RestClient restClient;
    private final NexxbotifyProperties properties;

    public NexxbotifyClient(RestClient.Builder builder, NexxbotifyProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeoutMs());
        requestFactory.setReadTimeout(properties.getReadTimeoutMs());
        RestClient.Builder configured = builder
                .requestFactory(requestFactory)
                .defaultHeaders(headers -> headers.setContentType(MediaType.APPLICATION_JSON));
        if (properties.getBaseUrl() != null && !properties.getBaseUrl().isBlank()) {
            configured = configured.baseUrl(properties.getBaseUrl());
        }
        this.restClient = configured.build();
    }

    /** Whether a nexxbotify base URL is configured. When {@code false}, the
     * features that deliver codes/links through this client are unavailable. */
    public boolean isConfigured() {
        return properties.getBaseUrl() != null && !properties.getBaseUrl().isBlank();
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
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound notFound) {
            if (organisation != null) {
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
            SendResponse response = restClient.post()
                    .uri("/send")
                    .body(body)
                    .retrieve()
                    .body(SendResponse.class);
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
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound e) {
            throw e;
        } catch (Exception e) {
            log.warn("nexxbotify send failed for flow {} to {}: {}",
                    flowId, identifier, e.getMessage());
            throw new IllegalStateException("The verification service is temporarily unavailable", e);
        }
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