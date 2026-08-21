package com.nexxserve.nauth.dto.response;

/**
 * Lightweight platform info embedded in user responses.
 */
public record PlatformSummary(Long id, String name, String slug) {
}
