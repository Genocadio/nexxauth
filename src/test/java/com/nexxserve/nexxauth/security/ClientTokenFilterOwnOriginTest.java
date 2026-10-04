package com.nexxserve.nexxauth.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Resolution of this server's own origin used by the {@code ClientTokenFilter}
 * cross-origin guard.
 *
 * <p>The regression these tests pin: {@code ownOrigin} used to read the raw
 * {@code X-Forwarded-Host} header. A proxy that <em>appends</em> to that header
 * (nginx's default {@code $proxy_add_x_forwarded_host}) produces a
 * comma-joined value such as {@code "evil.example, auth.nexxserve.dev"}, which
 * can never equal a real {@code Origin} header and therefore rejected every
 * legitimate same-origin browser request with HTTP 403. The guard now resolves
 * the origin from the container's parsed view of the request.
 */
class ClientTokenFilterOwnOriginTest {

    private static MockHttpServletRequest request(String serverName, String scheme, int port) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServerName(serverName);
        request.setScheme(scheme);
        request.setServerPort(port);
        return request;
    }

    @Test
    void resolvesStandardHttpsOriginWithoutPortSuffix() {
        assertThat(ClientTokenFilter.ownOrigin(request("auth.nexxerve.dev", "https", 443)))
                .isEqualTo("https://auth.nexxerve.dev");
    }

    @Test
    void keepsNonDefaultPortInOrigin() {
        assertThat(ClientTokenFilter.ownOrigin(request("localhost", "http", 8080)))
                .isEqualTo("http://localhost:8080");
    }

    @Test
    void omitsDefaultHttpPortSuffix() {
        assertThat(ClientTokenFilter.ownOrigin(request("auth.nexxerve.dev", "http", 80)))
                .isEqualTo("http://auth.nexxerve.dev");
    }

    @Test
    void ignoresAppendedForwardedHostListInsteadOfBuildingMalformedOrigin() {
        MockHttpServletRequest request = request("auth.nexxerve.dev", "https", 443);
        request.addHeader("X-Forwarded-Host", "evil.example, auth.nexxerve.dev");

        // The container-parsed server name wins; the comma-joined list must not
        // leak into the computed origin, otherwise every same-origin browser
        // request would be treated as cross-origin.
        assertThat(ClientTokenFilter.ownOrigin(request))
                .isEqualTo("https://auth.nexxerve.dev");
    }

    @Test
    void ignoresSpoofedForwardedHostHeader() {
        MockHttpServletRequest request = request("auth.nexxerve.dev", "https", 443);
        request.addHeader("X-Forwarded-Host", "evil.example");

        assertThat(ClientTokenFilter.ownOrigin(request))
                .isEqualTo("https://auth.nexxerve.dev");
    }

    @Test
    void returnsNullWhenServerNameIsMissing() {
        assertThat(ClientTokenFilter.ownOrigin(request(null, "https", 443))).isNull();
        assertThat(ClientTokenFilter.ownOrigin(request("   ", "https", 443))).isNull();
    }
}