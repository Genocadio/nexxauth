package com.nexxserve.nexxauth.controller;

import com.nexxserve.nexxauth.dto.request.UpdateUserRequest;
import com.nexxserve.nexxauth.dto.response.PlatformUserResponse;
import com.nexxserve.nexxauth.security.AuthenticatedUser;
import com.nexxserve.nexxauth.service.PlatformUserService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
public class PlatformUserController {

    private final PlatformUserService platformUserService;

    public PlatformUserController(PlatformUserService platformUserService) {
        this.platformUserService = platformUserService;
    }

    @GetMapping("/{id}")
    public PlatformUserResponse getUser(@PathVariable Long id,
                                        @AuthenticationPrincipal AuthenticatedUser requester) {
        return platformUserService.getUser(id, requester);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_USER')")
    public PlatformUserResponse updateUser(@PathVariable Long id,
                                           @AuthenticationPrincipal AuthenticatedUser requester,
                                           @Valid @RequestBody UpdateUserRequest request) {
        return platformUserService.updateUser(id, requester, request);
    }
}
