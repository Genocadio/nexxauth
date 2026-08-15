package com.nexxserve.nexxauth.security;

import com.nexxserve.nexxauth.exception.ErrorResponseWriter;
import com.nexxserve.nexxauth.exception.RequestBodyTooLargeException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ReadListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Rejects request bodies larger than {@code app.http.max-body-bytes} (413)
 * before they are ever parsed — a cheap DoS guard so an oversized JSON payload
 * cannot be buffered into memory. Runs before the rate limiter and security
 * chain.
 *
 * Two layers: requests that declare a {@code Content-Length} are rejected
 * immediately from the header; requests without one (chunked transfer) are
 * read through a capped stream that aborts as soon as the limit is exceeded,
 * so no body can ever be buffered beyond the cap either way.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class PayloadSizeFilter extends OncePerRequestFilter {

    private final HttpProperties properties;
    private final ErrorResponseWriter errorResponseWriter;

    public PayloadSizeFilter(HttpProperties properties, ErrorResponseWriter errorResponseWriter) {
        this.properties = properties;
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long maxBytes = properties.maxBodyBytes();
        String contentLength = request.getHeader(HttpHeaders.CONTENT_LENGTH);
        if (contentLength != null) {
            try {
                if (Long.parseLong(contentLength) > maxBytes) {
                    errorResponseWriter.write(response, HttpStatus.PAYLOAD_TOO_LARGE,
                            "Request body too large (max " + maxBytes + " bytes)",
                            request.getRequestURI());
                    return;
                }
            } catch (NumberFormatException ignored) {
                // Malformed Content-Length: let the container reject the request.
            }
        }
        try {
            filterChain.doFilter(new CappedBodyRequest(request, maxBytes), response);
        } catch (RequestBodyTooLargeException e) {
            errorResponseWriter.write(response, HttpStatus.PAYLOAD_TOO_LARGE,
                    "Request body too large (max " + maxBytes + " bytes)",
                    request.getRequestURI());
        }
    }

    /** Caps the body stream at {@code maxBytes}; reading past the cap throws
     * {@link RequestBodyTooLargeException}. */
    static final class CappedBodyRequest extends HttpServletRequestWrapper {

        private final long maxBytes;
        private ServletInputStream inputStream;
        private BufferedReader reader;

        CappedBodyRequest(HttpServletRequest request, long maxBytes) {
            super(request);
            this.maxBytes = maxBytes;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (inputStream == null) {
                inputStream = new CappedInputStream(super.getInputStream(), maxBytes);
            }
            return inputStream;
        }

        @Override
        public BufferedReader getReader() throws IOException {
            // Cache the reader over the single capped stream: the servlet
            // contract expects a stable reader, and a second call must not
            // wrap a fresh stream over the already-consumed input.
            if (reader == null) {
                reader = new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
            }
            return reader;
        }
    }

    static final class CappedInputStream extends ServletInputStream {

        private final InputStream delegate;
        private final long maxBytes;
        private long readBytes;
        private boolean eof;

        CappedInputStream(InputStream delegate, long maxBytes) {
            this.delegate = delegate;
            this.maxBytes = maxBytes;
        }

        @Override
        public int read() throws IOException {
            int value = delegate.read();
            if (value < 0) {
                eof = true;
                return -1;
            }
            readBytes++;
            if (readBytes > maxBytes) {
                throw new RequestBodyTooLargeException(maxBytes);
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            // Probe one byte past the limit so a body of exactly maxBytes reads
            // to EOF normally and only a truly larger one trips the guard.
            long remaining = maxBytes - readBytes;
            int toRead = (int) Math.min(length, remaining + 1);
            int read = delegate.read(buffer, offset, toRead);
            if (read < 0) {
                eof = true;
                return -1;
            }
            readBytes += read;
            if (readBytes > maxBytes) {
                throw new RequestBodyTooLargeException(maxBytes);
            }
            return read;
        }

        @Override
        public boolean isFinished() {
            return eof;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            throw new UnsupportedOperationException("Async reads are not supported");
        }
    }
}
