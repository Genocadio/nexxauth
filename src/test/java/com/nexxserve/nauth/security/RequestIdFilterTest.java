package com.nexxserve.nauth.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sanitization of the client-supplied {@code X-Request-Id} header: control
 * characters must not reach the logs, blank values fall back to a generated id
 * and long values are capped.
 */
class RequestIdFilterTest {

    @Test
    void nullAndBlankHeadersFallBackToGeneratedId() {
        assertThat(RequestIdFilter.sanitize(null)).isNull();
        assertThat(RequestIdFilter.sanitize("")).isNull();
        assertThat(RequestIdFilter.sanitize("   ")).isNull();
    }

    @Test
    void controlCharactersAreStripped() {
        assertThat(RequestIdFilter.sanitize("ab\u0000cd\u0007\tef"))
                .isEqualTo("abcdef");
    }

    @Test
    void valueIsTrimmed() {
        assertThat(RequestIdFilter.sanitize("  abc-123  ")).isEqualTo("abc-123");
    }

    @Test
    void overlongValueIsCapped() {
        String value = "x".repeat(200);
        String sanitized = RequestIdFilter.sanitize(value);

        assertThat(sanitized).hasSize(RequestIdFilter.MAX_LENGTH);
    }

    @Test
    void plainValuePassesThroughUntouched() {
        assertThat(RequestIdFilter.sanitize("req_7f4a2c-01")).isEqualTo("req_7f4a2c-01");
    }
}
