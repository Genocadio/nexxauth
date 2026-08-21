package com.nexxserve.nauth.dto.response;

import java.time.Instant;

public record PlatformResponse(
        Long id,
        String name,
        String slug,
        long userCount,
        String apiBaseUrl,
        Instant createdAt
) {
}
