package com.nexxserve.nauth.controller;

import com.nexxserve.nauth.dto.request.ChangePasswordRequest;
import com.nexxserve.nauth.dto.request.LoginRequest;
import com.nexxserve.nauth.dto.request.LogoutRequest;
import com.nexxserve.nauth.dto.request.RefreshTokenRequest;
import com.nexxserve.nauth.dto.request.RegisterRequest;
import com.nexxserve.nauth.dto.request.UpdateProfileRequest;
import com.nexxserve.nauth.dto.response.AuthResponse;
import com.nexxserve.nauth.dto.response.PlatformUserResponse;
import com.nexxserve.nauth.security.AuthenticatedUser;
import com.nexxserve.nauth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public PlatformUserResponse me(@AuthenticationPrincipal AuthenticatedUser requester) {
        return authService.me(requester);
    }

    @PatchMapping("/me")
    public PlatformUserResponse updateProfile(@AuthenticationPrincipal AuthenticatedUser requester,
                                              @Valid @RequestBody UpdateProfileRequest request) {
        return authService.updateProfile(requester, request);
    }

    @PostMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal AuthenticatedUser requester,
                                               @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(requester, request);
        return ResponseEntity.noContent().build();
    }
}
