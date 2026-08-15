package com.nexxserve.nexxauth.controller;

import com.nexxserve.nexxauth.dto.request.LogoutRequest;
import com.nexxserve.nexxauth.dto.request.OrgLoginRequest;
import com.nexxserve.nexxauth.dto.request.OrgRegisterRequest;
import com.nexxserve.nexxauth.dto.request.RefreshTokenRequest;
import com.nexxserve.nexxauth.dto.response.OrgAuthResponse;
import com.nexxserve.nexxauth.service.OrganisationAuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Organisation-level authentication, independent of the platform auth flow.
 * Public endpoints (like platform auth): register and login create an org
 * session signed by the organisation's own RSA key.
 */
@RestController
@RequestMapping("/api/v1/platforms/{slug}/auth")
public class OrganisationAuthController {

    private final OrganisationAuthService authService;

    public OrganisationAuthController(OrganisationAuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public OrgAuthResponse register(@PathVariable String slug,
                                    @Valid @RequestBody OrgRegisterRequest request) {
        return authService.register(slug, request);
    }

    @PostMapping("/login")
    public OrgAuthResponse login(@PathVariable String slug,
                                 @Valid @RequestBody OrgLoginRequest request) {
        return authService.login(slug, request);
    }

    @PostMapping("/refresh")
    public OrgAuthResponse refresh(@PathVariable String slug,
                                   @Valid @RequestBody RefreshTokenRequest request) {
        return authService.refresh(slug, request.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> logout(@PathVariable String slug,
                                       @Valid @RequestBody LogoutRequest request) {
        authService.logout(slug, request.refreshToken());
        return ResponseEntity.noContent().build();
    }
}
