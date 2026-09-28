package com.nexxserve.nexxauth.util;

import java.util.Locale;

/**
 * Normalizes email addresses before storage and lookup so {@code Ada@x.com}
 * and {@code ada@x.com} are the same account.
 */
public final class Emails {

    private Emails() {
    }

    public static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean looksLikeEmail(String value) {
        if (value == null) return false;
        String trimmed = value.trim();
        return trimmed.contains("@") && trimmed.indexOf('@') > 0 && trimmed.indexOf('@') < trimmed.length() - 1;
    }
}
