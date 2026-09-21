package com.nexxserve.nexxauth;

import com.nexxserve.nexxauth.entity.VerificationChannel;
import com.nexxserve.nexxauth.entity.VerificationDelivery;
import com.nexxserve.nexxauth.service.NexxbotifyClient;
import com.nexxserve.nexxauth.service.NexxbotifyProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the delivery judgment in {@link NexxbotifyClient}: a send is
 * only considered delivered when nexxbotify reports at least one result with
 * status {@code sent}. In particular, an empty (or missing) result list must be
 * a failure even when nexxbotify answers {@code status: "completed"} — that is
 * the signal that no channel matched the receiver and nothing was sent.
 */
class NexxbotifyClientTest {

    private RestClient.Builder builder;
    private RestClient restClient;
    private RestClient.RequestBodyUriSpec uriSpec;
    private RestClient.RequestBodySpec bodySpec;
    private RestClient.ResponseSpec responseSpec;
    private NexxbotifyClient client;

    @BeforeEach
    void setUp() {
        builder = mock(RestClient.Builder.class);
        restClient = mock(RestClient.class);
        uriSpec = mock(RestClient.RequestBodyUriSpec.class);
        bodySpec = mock(RestClient.RequestBodySpec.class);
        responseSpec = mock(RestClient.ResponseSpec.class);

        when(builder.baseUrl(anyString())).thenReturn(builder);
        when(builder.requestFactory(any(ClientHttpRequestFactory.class))).thenReturn(builder);
        when(builder.defaultHeaders(any())).thenReturn(builder);
        when(builder.build()).thenReturn(restClient);

        when(restClient.post()).thenReturn(uriSpec);
        when(uriSpec.uri("/send")).thenReturn(bodySpec);
        when(uriSpec.uri("/flows")).thenReturn(bodySpec);
        when(uriSpec.uri(org.mockito.ArgumentMatchers.eq("/flows/{id}/channels"), any(Object[].class))).thenReturn(bodySpec);
        when(bodySpec.body(any(NexxbotifyClient.SendRequest.class))).thenReturn(bodySpec);
        when(bodySpec.body(any(NexxbotifyClient.CreateFlowRequest.class))).thenReturn(bodySpec);
        when(bodySpec.body(any(NexxbotifyClient.CreateChannelRequest.class))).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenReturn(responseSpec);

        NexxbotifyProperties properties = new NexxbotifyProperties();
        properties.setBaseUrl("http://nexxbotify.example:8080");
        client = new NexxbotifyClient(builder, properties);
    }

    private void respond(NexxbotifyClient.SendResponse response) {
        when(responseSpec.body(NexxbotifyClient.SendResponse.class)).thenReturn(response);
    }

    private NexxbotifyClient.SendResult sent(String receiver, String channel) {
        return new NexxbotifyClient.SendResult(receiver, channel, "sent", null);
    }

    private NexxbotifyClient.SendResult failed(String receiver, String channel, String error) {
        return new NexxbotifyClient.SendResult(receiver, channel, "failed", error);
    }

    @Test
    void emptyResultsWithCompletedStatusIsADeliveryFailure() {
        respond(new NexxbotifyClient.SendResponse("ntf_1", "completed", List.of()));

        assertThatThrownBy(() -> client.send(VerificationDelivery.OTP, VerificationChannel.EMAIL,
                "a@b.com", Map.of("code", "123456")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("could not be delivered");
    }

    @Test
    void missingResultsWithCompletedStatusIsADeliveryFailure() {
        respond(new NexxbotifyClient.SendResponse("ntf_2", "completed", null));

        assertThatThrownBy(() -> client.send(VerificationDelivery.OTP, VerificationChannel.SMS,
                "+15551234567", Map.of("code", "123456")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("could not be delivered");
    }

    @Test
    void anySentResultCountsAsDelivered() {
        respond(new NexxbotifyClient.SendResponse("ntf_3", "completed",
                List.of(sent("a@b.com", "email"))));

        assertThatCode(() -> client.send(VerificationDelivery.OTP, VerificationChannel.EMAIL,
                "a@b.com", Map.of("code", "123456"))).doesNotThrowAnyException();
    }

    @Test
    void onlyFailedOrSkippedResultsIsADeliveryFailure() {
        respond(new NexxbotifyClient.SendResponse("ntf_4", "partial_failure",
                List.of(failed("a@b.com", "email", "provider rejected"))));

        assertThatThrownBy(() -> client.send(VerificationDelivery.OTP, VerificationChannel.EMAIL,
                "a@b.com", Map.of("code", "123456")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("could not be delivered");
    }

    @Test
    void sendFailsCleanlyWhenNotifierNotConfigured() {
        // No base URL configured: the client is inert and fails fast instead
        // of attempting a network call.
        NexxbotifyClient unconfigured = new NexxbotifyClient(builder, new NexxbotifyProperties());

        assertThatThrownBy(() -> unconfigured.send(VerificationDelivery.OTP, VerificationChannel.EMAIL,
                "a@b.com", Map.of("code", "123456")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not configured");

        verify(restClient, org.mockito.Mockito.never()).post();
    }

    @Test
    void requestBodyUsesFlowIdAndOnlyTheChannelAddress() {
        respond(new NexxbotifyClient.SendResponse("ntf_5", "completed",
                List.of(sent("+15551234567", "sms"))));

        client.send(VerificationDelivery.OTP, VerificationChannel.SMS,
                "+15551234567", Map.of("code", "123456"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<NexxbotifyClient.SendRequest> captor =
                ArgumentCaptor.forClass(NexxbotifyClient.SendRequest.class);
        verify(bodySpec).body(captor.capture());
        NexxbotifyClient.SendRequest sent = captor.getValue();

        assertThat(sent.flow_id()).isEqualTo("otp_sms");
        assertThat(sent.variables()).isEqualTo(Map.of("code", "123456"));
        assertThat(sent.receivers()).containsExactly(Map.of("phone", "+15551234567"));
    }

    @Test
    void ensureOrganisationFlowCreatesEmailAndSmsChannels() {
        com.nexxserve.nexxauth.entity.Organisation org = new com.nexxserve.nexxauth.entity.Organisation();
        org.setId(42L);
        org.setName("Acme Healthcare");
        org.setSlug("acme");

        client.ensureOrganisationFlow(org, true);

        ArgumentCaptor<NexxbotifyClient.CreateFlowRequest> captor =
                ArgumentCaptor.forClass(NexxbotifyClient.CreateFlowRequest.class);
        verify(bodySpec).body(captor.capture());
        NexxbotifyClient.CreateFlowRequest created = captor.getValue();

        assertThat(created.id()).isEqualTo("org_42_auth");
        assertThat(created.name()).isEqualTo("Acme Healthcare Auth");
        assertThat(created.channels()).hasSize(2);
        assertThat(created.channels().stream().map(NexxbotifyClient.CreateChannelRequest::channel))
                .containsExactly("email", "sms");
    }

    @Test
    void ensureOrganisationFlowCreatesEmailOnlyWhenSmsFalse() {
        com.nexxserve.nexxauth.entity.Organisation org = new com.nexxserve.nexxauth.entity.Organisation();
        org.setId(7L);
        org.setName("Beta Clinic");
        org.setSlug("beta");

        client.ensureOrganisationFlow(org, false);

        ArgumentCaptor<NexxbotifyClient.CreateFlowRequest> captor =
                ArgumentCaptor.forClass(NexxbotifyClient.CreateFlowRequest.class);
        verify(bodySpec).body(captor.capture());
        NexxbotifyClient.CreateFlowRequest created = captor.getValue();

        assertThat(created.id()).isEqualTo("org_7_auth");
        assertThat(created.channels()).hasSize(1);
        assertThat(created.channels().get(0).channel()).isEqualTo("email");
    }

    @Test
    void sendForOrganisationUsesDedicatedFlowId() {
        respond(new NexxbotifyClient.SendResponse("ntf_6", "completed",
                List.of(sent("user@example.com", "email"))));

        com.nexxserve.nexxauth.entity.Organisation org = new com.nexxserve.nexxauth.entity.Organisation();
        org.setId(99L);
        org.setName("Delta Org");
        org.setSlug("delta");

        client.sendForOrganisation(org, VerificationDelivery.OTP, VerificationChannel.EMAIL,
                "user@example.com", Map.of("code", "654321"));

        ArgumentCaptor<NexxbotifyClient.SendRequest> captor =
                ArgumentCaptor.forClass(NexxbotifyClient.SendRequest.class);
        verify(bodySpec).body(captor.capture());
        NexxbotifyClient.SendRequest sent = captor.getValue();

        assertThat(sent.flow_id()).isEqualTo("org_99_auth");
        assertThat(sent.variables()).isEqualTo(Map.of("code", "654321"));
        assertThat(sent.receivers()).containsExactly(Map.of("email", "user@example.com"));
    }
}