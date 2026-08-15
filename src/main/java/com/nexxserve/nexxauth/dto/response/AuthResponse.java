package com.nexxserve.nexxauth.dto.response;

/**
 * Returned by register / login / refresh.
 */
public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresInSeconds,
        PlatformUserResponse user
) {
    public static AuthResponse of(String accessToken, String refreshToken, long expiresInSeconds, PlatformUserResponse user) {
        return new AuthResponse(accessToken, refreshToken, "Bearer", expiresInSeconds, user);
    }
}
