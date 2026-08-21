package com.nexxserve.nexxauth.controller;

import com.nexxserve.nexxauth.dto.request.AddPlatformUserRequest;
import com.nexxserve.nexxauth.dto.response.PlatformResponse;
import com.nexxserve.nexxauth.dto.response.PlatformUserResponse;
import com.nexxserve.nexxauth.security.AuthenticatedUser;
import com.nexxserve.nexxauth.service.PlatformService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping
public class PlatformController {

    private final PlatformService platformService;

    public PlatformController(PlatformService platformService) {
        this.platformService = platformService;
    }

    @GetMapping("/{slug}")
    public PlatformResponse getPlatform(@PathVariable String slug,
                                        @AuthenticationPrincipal AuthenticatedUser requester) {
        return platformService.getPlatform(slug, requester);
    }

    @GetMapping("/{slug}/users")
    public List<PlatformUserResponse> getUsers(@PathVariable String slug,
                                               @AuthenticationPrincipal AuthenticatedUser requester) {
        return platformService.getUsers(slug, requester);
    }

    @PostMapping("/{slug}/users")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('SUPER_USER')")
    public PlatformUserResponse addUser(@PathVariable String slug,
                                        @AuthenticationPrincipal AuthenticatedUser requester,
                                        @Valid @RequestBody AddPlatformUserRequest request) {
        return platformService.addUser(slug, requester, request);
    }
}
