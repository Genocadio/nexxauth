package com.nexxserve.nauth.security;

import com.nexxserve.nauth.exception.RequestBodyTooLargeException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the capped stream that guards request bodies without a
 * usable {@code Content-Length} (chunked transfer). A body of exactly the
 * limit must read to EOF normally; only a genuinely larger body trips the 413.
 */
class PayloadSizeFilterTest {

    private byte[] bytes(String body) {
        return body.getBytes(StandardCharsets.UTF_8);
    }

    private String readAll(PayloadSizeFilter.CappedInputStream in) throws IOException {
        StringBuilder out = new StringBuilder();
        byte[] buffer = new byte[3];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
        }
        return out.toString();
    }

    @Test
    void bodyWithinLimitReadsToEof() throws IOException {
        String body = "hello-world";
        var in = new PayloadSizeFilter.CappedInputStream(
                new ByteArrayInputStream(bytes(body)), 1_000);

        assertThat(readAll(in)).isEqualTo(body);
        assertThat(in.isFinished()).isTrue();
    }

    @Test
    void bodyExactlyAtLimitReadsToEof() throws IOException {
        String body = "0123456789";
        var in = new PayloadSizeFilter.CappedInputStream(
                new ByteArrayInputStream(bytes(body)), 10);

        assertThat(readAll(in)).isEqualTo(body);
        assertThat(in.isFinished()).isTrue();
    }

    @Test
    void largerBodyThrowsWhenReadingPastLimit() {
        String body = "0123456789X";
        var in = new PayloadSizeFilter.CappedInputStream(
                new ByteArrayInputStream(bytes(body)), 10);

        assertThatThrownBy(() -> readAll(in))
                .isInstanceOf(RequestBodyTooLargeException.class)
                .hasMessageContaining("10");
    }

    @Test
    void bulkReadSplittingAcrossLimitThrows() throws IOException {
        String body = "0123456789XY";
        var in = new PayloadSizeFilter.CappedInputStream(
                new ByteArrayInputStream(bytes(body)), 10);

        byte[] buffer = new byte[4];
        assertThat(in.read(buffer)).isEqualTo(4);
        assertThat(in.read(buffer)).isEqualTo(4);
        assertThatThrownBy(() -> in.read(buffer))
                .isInstanceOf(RequestBodyTooLargeException.class);
    }

    @Test
    void singleByteReadOverLimitThrows() throws IOException {
        String body = "0123456789X";
        var in = new PayloadSizeFilter.CappedInputStream(
                new ByteArrayInputStream(bytes(body)), 10);

        for (int i = 0; i < 10; i++) {
            in.read();
        }
        assertThatThrownBy(in::read)
                .isInstanceOf(RequestBodyTooLargeException.class);
    }
}
