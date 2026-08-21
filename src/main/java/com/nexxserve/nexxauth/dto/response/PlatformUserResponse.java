package com.nexxserve.nexxauth.dto.response;

import com.nexxserve.nexxauth.entity.Role;

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
