package com.nexxserve.nexxauth.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Verification code/link parameters ({@code app.verification.*}): the raw
 * value length, time-to-live, resend throttle and max verify attempts.
 */
@ConfigurationProperties(prefix = "app.verification")
public class VerificationProperties {

    private String baseUrl = "http://localhost:8080";

    /** Number of digits in a numeric OTP. */
    private int otpLength = 6;

    private Duration otpTtl = Duration.ofMinutes(10);

    /** How long a magic-link token stays valid. */
    private Duration linkTtl = Duration.ofMinutes(30);

    /** Minimum interval before a new code/link can be requested for the same
     * identifier — throttles SMS/email bombing. */
    private Duration resendInterval = Duration.ofSeconds(60);

    /** Max failed attempts before an OTP is invalidated. */
    private int maxAttempts = 5;

    /** How long a server-sent login challenge (2FA / verify at next login)
     * stays usable after the password login that produced it. */
    private Duration challengeTtl = Duration.ofMinutes(5);

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public int getOtpLength() {
        return otpLength;
    }

    public void setOtpLength(int otpLength) {
        this.otpLength = otpLength;
    }

    public Duration getOtpTtl() {
        return otpTtl;
    }

    public void setOtpTtl(Duration otpTtl) {
        this.otpTtl = otpTtl;
    }

    public Duration getLinkTtl() {
        return linkTtl;
    }

    public void setLinkTtl(Duration linkTtl) {
        this.linkTtl = linkTtl;
    }

    public Duration getResendInterval() {
        return resendInterval;
    }

    public void setResendInterval(Duration resendInterval) {
        this.resendInterval = resendInterval;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public Duration getChallengeTtl() {
        return challengeTtl;
    }

    public void setChallengeTtl(Duration challengeTtl) {
        this.challengeTtl = challengeTtl;
    }

    /** TTL for a delivery type. */
    public Duration ttlFor(com.nexxserve.nexxauth.entity.VerificationDelivery delivery) {
        return switch (delivery) {
            case OTP -> otpTtl;
            case LINK -> linkTtl;
        };
    }
}