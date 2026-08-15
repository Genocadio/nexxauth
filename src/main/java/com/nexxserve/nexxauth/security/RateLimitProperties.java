package com.nexxserve.nexxauth.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Per-IP rate limits for the auth endpoints ({@code app.rate-limit.*}). Each
 * limit is a token bucket: {@code capacity} requests are allowed immediately,
 * then {@code refillPerMinute} tokens are restored every minute.
 */
@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {

    private boolean enabled = true;

    private Limit login = new Limit(5, 1);

    private Limit register = new Limit(3, 1);

    /** Refresh-token rotation: higher cap than login because legit clients
     * rotate on every access-token expiry (15 min); still throttles token
     * enumeration / replay probing. */
    private Limit refresh = new Limit(10, 2);

    /** Public slug suggestions (register form): debounced as the user types,
     * so the cap is generous; still stops slug-space enumeration per IP. */
    private Limit suggestions = new Limit(60, 30);

    /** When true, the client IP is read from the first X-Forwarded-For entry (behind a trusted proxy). */
    private boolean useForwardedFor = false;

    /** Backing store: "in-memory" (default) or "redis". */
    private String store = "in-memory";

    private Redis redis = new Redis();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getStore() {
        return store;
    }

    public void setStore(String store) {
        this.store = store;
    }

    public Redis getRedis() {
        return redis;
    }

    public void setRedis(Redis redis) {
        this.redis = redis;
    }

    public Limit getLogin() {
        return login;
    }

    public void setLogin(Limit login) {
        this.login = login;
    }

    public Limit getRegister() {
        return register;
    }

    public void setRegister(Limit register) {
        this.register = register;
    }

    public Limit getRefresh() {
        return refresh;
    }

    public void setRefresh(Limit refresh) {
        this.refresh = refresh;
    }

    public Limit getSuggestions() {
        return suggestions;
    }

    public void setSuggestions(Limit suggestions) {
        this.suggestions = suggestions;
    }

    public boolean isUseForwardedFor() {
        return useForwardedFor;
    }

    public void setUseForwardedFor(boolean useForwardedFor) {
        this.useForwardedFor = useForwardedFor;
    }

    /** Connection details for the Redis-backed store. */
    public static class Redis {

        private String host = "localhost";

        private int port = 6379;

        private String password;

        private boolean ssl = false;

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public boolean isSsl() {
            return ssl;
        }

        public void setSsl(boolean ssl) {
            this.ssl = ssl;
        }
    }

    public static class Limit {

        private int capacity;

        private int refillPerMinute;

        public Limit() {
        }

        public Limit(int capacity, int refillPerMinute) {
            this.capacity = capacity;
            this.refillPerMinute = refillPerMinute;
        }

        public int getCapacity() {
            return capacity;
        }

        public void setCapacity(int capacity) {
            this.capacity = capacity;
        }

        public int getRefillPerMinute() {
            return refillPerMinute;
        }

        public void setRefillPerMinute(int refillPerMinute) {
            this.refillPerMinute = refillPerMinute;
        }
    }
}
