package com.nexxserve.nexxauth.controller;

import com.nexxserve.nexxauth.dto.request.PasswordResetConfirmRequest;
import com.nexxserve.nexxauth.dto.request.VerificationRequest;
import com.nexxserve.nexxauth.dto.request.VerificationVerifyRequest;
import com.nexxserve.nexxauth.dto.response.OrgAuthResponse;
import com.nexxserve.nexxauth.dto.response.VerificationRequestResponse;
import com.nexxserve.nexxauth.entity.OrganisationUser;
import com.nexxserve.nexxauth.security.RateLimitProperties;
import com.nexxserve.nexxauth.service.OrganisationAuthService;
import com.nexxserve.nexxauth.service.OrganisationVerificationService;
import com.nexxserve.nexxauth.util.ClientIps;
import com.nexxserve.nexxauth.util.Hostnames;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    static final String AUTH_HEADER = "Authorization";

    private final OrganisationVerificationService verificationService;
    private final OrganisationAuthService authService;
    private final RateLimitProperties rateLimitProperties;

    public OrganisationVerificationController(OrganisationVerificationService verificationService,
                                              OrganisationAuthService authService,
                                              RateLimitProperties rateLimitProperties) {
        this.verificationService = verificationService;
        this.authService = authService;
        this.rateLimitProperties = rateLimitProperties;
    }

    /** Sends a numeric OTP or magic link to the given identifier. */
    @PostMapping("/{slug}/auth/verifications/request")
    public VerificationRequestResponse request(@PathVariable String slug,
                                               @RequestHeader(value = CLIENT_ID_HEADER, required = false) String clientId,
                                               @Valid @RequestBody VerificationRequest request) {
        return verificationService.request(slug, request, clientId);
    }

    /** Confirms a numeric OTP for email or phone verification. When an
     * {@code Authorization: Bearer <actionToken>} header is supplied, also
     * exchanges the action token for a full {@link OrgAuthResponse} (200 OK);
     * otherwise returns 204 No Content. */
    @PostMapping("/{slug}/auth/verifications/verify")
    public ResponseEntity<OrgAuthResponse> verify(@PathVariable String slug,
                                                  @RequestHeader(value = CLIENT_ID_HEADER, required = false) String clientId,
                                                  @RequestHeader(value = AUTH_HEADER, required = false) String authHeader,
                                                  @Valid @RequestBody VerificationVerifyRequest request,
                                                  HttpServletRequest httpRequest) {
        OrganisationUser verifiedUser = verificationService.verifyOtp(slug, request, clientId, authHeader);
        if (authHeader != null && authHeader.regionMatches(true, 0, "Bearer ", 0, 7)
                && !authHeader.substring(7).isBlank()) {
            String ip = ClientIps.resolve(httpRequest, rateLimitProperties.isUseForwardedFor());
            String hostname = Hostnames.resolve(httpRequest, ip);
            OrgAuthResponse authResponse = authService.issueTokensForVerifiedUser(
                    verifiedUser, clientId, ip, httpRequest.getHeader("User-Agent"), hostname);
            return ResponseEntity.ok(authResponse);
        }
        return ResponseEntity.noContent().build();
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
                                     @RequestHeader(value = AUTH_HEADER, required = false) String authHeader,
                                     @Valid @RequestBody PasswordResetConfirmRequest request) {
        verificationService.confirmPasswordReset(slug, request, clientId, authHeader);
    }
}