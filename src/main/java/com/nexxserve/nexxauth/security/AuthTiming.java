package com.nexxserve.nexxauth.security;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Equalizes the time a login takes when the account (or its password) does not
 * exist, so an attacker cannot distinguish "unknown email/identifier" from
 * "wrong password" by response time. Runs a BCrypt comparison against a
 * pre-computed dummy hash - the cost is the same as a real verification.
 */
@Component
public class AuthTiming {

    private final PasswordEncoder passwordEncoder;
    private final String dummyHash;

    public AuthTiming(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
        // Cost is dominated by BCrypt work, so a random password is fine.
        this.dummyHash = passwordEncoder.encode("timing-equalizer-" + UUID.randomUUID());
    }

    /**
     * Performs a BCrypt comparison that always yields {@code false}. Call it on
     * the unknown-account path so the response time matches a real password
     * check against a known account.
     */
    public boolean equalsUnknown(String rawPassword) {
        return passwordEncoder.matches(rawPassword, dummyHash);
    }
}
