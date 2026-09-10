package com.nexxserve.nexxauth.controller;

import com.nexxserve.nexxauth.dto.request.PasswordResetConfirmRequest;
import com.nexxserve.nexxauth.dto.request.VerificationRequest;
import com.nexxserve.nexxauth.dto.request.VerificationVerifyRequest;
import com.nexxserve.nexxauth.dto.response.VerificationRequestResponse;
import com.nexxserve.nexxauth.service.OrganisationVerificationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Organisation-level verification (OTP / magic link) delivered through
 * nexxbotify, and the password-reset flow. All endpoints are public — an
 * unauthenticated user must be able to ask for, receive and use a code/link.
 * The organisation is resolved from the {@code X-Client-Id} header, or from
 * {@code organisationId} in the body for the platform console portal flow.
 */
@RestController
public class OrganisationVerificationController {

    static final String CLIENT_ID_HEADER = "X-Client-Id";

    private final OrganisationVerificationService verificationService;

    public OrganisationVerificationController(OrganisationVerificationService verificationService) {
        this.verificationService = verificationService;
    }

    /** Sends a numeric OTP or magic link to the given identifier. */
    @PostMapping("/{slug}/auth/verifications/request")
    public VerificationRequestResponse request(@PathVariable String slug,
                                               @RequestHeader(value = CLIENT_ID_HEADER, required = false) String clientId,
                                               @Valid @RequestBody VerificationRequest request) {
        return verificationService.request(slug, request, clientId);
    }

    /** Confirms a numeric OTP for email or phone verification. */
    @PostMapping("/{slug}/auth/verifications/verify")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verify(@PathVariable String slug,
                       @RequestHeader(value = CLIENT_ID_HEADER, required = false) String clientId,
                       @Valid @RequestBody VerificationVerifyRequest request) {
        verificationService.verifyOtp(slug, request, clientId);
    }

    /** Opens a magic link for email or phone verification. */
    @GetMapping("/{slug}/auth/verifications/complete")
    public Map<String, Object> complete(@PathVariable String slug,
                                        @RequestParam("token") String token) {
        verificationService.completeViaLink(slug, token);
        return Map.of("verified", true);
    }

    /** Final step of a password reset: code/link + new password. */
    @PostMapping("/{slug}/auth/password-reset/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmPasswordReset(@PathVariable String slug,
                                     @RequestHeader(value = CLIENT_ID_HEADER, required = false) String clientId,
                                     @Valid @RequestBody PasswordResetConfirmRequest request) {
        verificationService.confirmPasswordReset(slug, request, clientId);
    }
}