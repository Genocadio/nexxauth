package com.nexxserve.nexxauth.controller;

import com.nexxserve.nexxauth.dto.request.LogoutRequest;
import com.nexxserve.nexxauth.dto.request.OrgLoginRequest;
import com.nexxserve.nexxauth.dto.request.OrgRegisterRequest;
import com.nexxserve.nexxauth.dto.request.RefreshTokenRequest;
import com.nexxserve.nexxauth.dto.response.OrgAuthResponse;
import com.nexxserve.nexxauth.security.RateLimitProperties;
import com.nexxserve.nexxauth.service.OrganisationAuthService;
import com.nexxserve.nexxauth.util.ClientIps;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Organisation-level authentication, independent of the platform auth flow.
 * Public endpoints (like platform auth): register and login create an org
 * session signed by the organisation's own RSA key.
 * <p>
 * When the request carries an {@code X-Client-Id} header the organisation is
 * identified through the client (the client's organisation is authoritative
 * and no {@code organisationId} is needed in the body); without the header the
 * body's {@code organisationId} is required (server-side/platform-user flows).
 */
@RestController
@RequestMapping("/{slug}/auth")
public class OrganisationAuthController {

    static final String CLIENT_ID_HEADER = "X-Client-Id";

    private final OrganisationAuthService authService;
    private final RateLimitProperties rateLimitProperties;

    public OrganisationAuthController(OrganisationAuthService authService,
                                      RateLimitProperties rateLimitProperties) {
        this.authService = authService;
        this.rateLimitProperties = rateLimitProperties;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public OrgAuthResponse register(@PathVariable String slug,
                                    @RequestHeader(value = CLIENT_ID_HEADER, required = false) String clientId,
                                    @Valid @RequestBody OrgRegisterRequest request,
                                    HttpServletRequest httpRequest) {
        return authService.register(slug, request, clientId, resolveIp(httpRequest), httpRequest.getHeader("User-Agent"));
    }

    @PostMapping("/login")
    public OrgAuthResponse login(@PathVariable String slug,
                                 @RequestHeader(value = CLIENT_ID_HEADER, required = false) String clientId,
                                 @Valid @RequestBody OrgLoginRequest request,
                                 HttpServletRequest httpRequest) {
        return authService.login(slug, request, clientId, resolveIp(httpRequest), httpRequest.getHeader("User-Agent"));
    }

    @PostMapping("/refresh")
    public OrgAuthResponse refresh(@PathVariable String slug,
                                   @Valid @RequestBody RefreshTokenRequest request,
                                   HttpServletRequest httpRequest) {
        return authService.refresh(slug, request.refreshToken(),
                resolveIp(httpRequest), httpRequest.getHeader("User-Agent"));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> logout(@PathVariable String slug,
                                       @Valid @RequestBody LogoutRequest request) {
        authService.logout(slug, request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    private String resolveIp(HttpServletRequest request) {
        return ClientIps.resolve(request, rateLimitProperties.isUseForwardedFor());
    }
}
