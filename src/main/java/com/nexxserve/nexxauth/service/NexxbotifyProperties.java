package com.nexxserve.nexxauth.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Connection settings for the nexxbotify (notification) service
 * ({@code app.nexxbotify.*}). nexxauth calls {@code POST /send} with one of the
 * configured flow ids; the flow (template + channel) itself is created and
 * maintained in nexxbotify.
 */
@ConfigurationProperties(prefix = "app.nexxbotify")
public class NexxbotifyProperties {

    /** Base URL of nexxbotify, e.g. {@code http://nexxnotify:8080}. Leave
     * blank (or unset) to disable every feature that delivers codes or links
     * through nexxbotify — email/phone verification, password reset, OTP
     * login and 2FA challenges are then locked instead of failing at send
     * time. */
    private String baseUrl = "";

    /** Shared secret sent as {@code X-Api-Key} on every request to nexxnotify.
     * Must match {@code NEXXNOTIFY_API_KEY} set on the nexxnotify service.
     * Leave blank to skip authentication (not recommended in production). */
    private String apiKey = "";

    /** PEM or Base64 Ed25519 or RSA PKCS8 private key used to sign ephemeral
     * single-use tokens for nexxnotify. When set, each request sends a fresh
     * 60-second token with a unique JTI to prevent replay attacks. */
    private String privateKey = "";

    private int connectTimeoutMs = 5000;

    private int readTimeoutMs = 10000;

    /** Flow id for an OTP delivered by email. */
    private String otpEmailFlowId = "otp_email";

    /** Flow id for an OTP delivered by SMS. */
    private String otpSmsFlowId = "otp_sms";

    /** Flow id for a magic link delivered by email. */
    private String linkEmailFlowId = "link_email";

    /** Flow id for a magic link delivered by SMS. */
    private String linkSmsFlowId = "link_sms";

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getPrivateKey() {
        return privateKey;
    }

    public void setPrivateKey(String privateKey) {
        this.privateKey = privateKey;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }

    public String getOtpEmailFlowId() {
        return otpEmailFlowId;
    }

    public void setOtpEmailFlowId(String otpEmailFlowId) {
        this.otpEmailFlowId = otpEmailFlowId;
    }

    public String getOtpSmsFlowId() {
        return otpSmsFlowId;
    }

    public void setOtpSmsFlowId(String otpSmsFlowId) {
        this.otpSmsFlowId = otpSmsFlowId;
    }

    public String getLinkEmailFlowId() {
        return linkEmailFlowId;
    }

    public void setLinkEmailFlowId(String linkEmailFlowId) {
        this.linkEmailFlowId = linkEmailFlowId;
    }

    public String getLinkSmsFlowId() {
        return linkSmsFlowId;
    }

    public void setLinkSmsFlowId(String linkSmsFlowId) {
        this.linkSmsFlowId = linkSmsFlowId;
    }

    /** Flow id for a delivery type + channel combination. */
    public String flowIdFor(com.nexxserve.nexxauth.entity.VerificationDelivery delivery,
                            com.nexxserve.nexxauth.entity.VerificationChannel channel) {
        return switch (delivery) {
            case OTP -> switch (channel) {
                case EMAIL -> otpEmailFlowId;
                case SMS -> otpSmsFlowId;
            };
            case LINK -> switch (channel) {
                case EMAIL -> linkEmailFlowId;
                case SMS -> linkSmsFlowId;
            };
        };
    }
}