package com.nexxserve.nauth.dto.response;

import com.nexxserve.nauth.entity.Role;

import java.time.Instant;

public record PlatformUserResponse(
        Long id,
        String firstName,
        String lastName,
        String email,
        String phone,
        Role role,
        boolean enabled,
        PlatformSummary platform,
        Instant createdAt
) {
}
