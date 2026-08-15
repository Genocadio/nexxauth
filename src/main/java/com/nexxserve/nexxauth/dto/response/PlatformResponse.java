package com.nexxserve.nexxauth.dto.response;

import java.time.Instant;

public record PlatformResponse(
        Long id,
        String name,
        String slug,
        long userCount,
        Instant createdAt
) {
}
