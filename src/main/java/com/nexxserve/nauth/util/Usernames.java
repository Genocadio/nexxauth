package com.nexxserve.nauth.util;

import java.util.Locale;

/**
 * Normalizes usernames before storage and lookup so {@code Bob} and
 * {@code bob} are the same account (mirror of {@link Emails} for emails).
 * Everything is stored lowercase, keeping the per-organisation unique
 * constraint case-insensitive by construction.
 */
public final class Usernames {

    private Usernames() {
    }

    public static String normalize(String username) {
        return username == null ? null : username.trim().toLowerCase(Locale.ROOT);
    }
}
