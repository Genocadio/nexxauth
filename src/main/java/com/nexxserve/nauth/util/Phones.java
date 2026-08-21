package com.nexxserve.nauth.util;

/**
 * Normalizes phone numbers before storage and lookup so {@code +1 (555) 123-4567}
 * and {@code +15551234567} are the same number. Keeps the leading {@code +},
 * strips the usual separators (spaces, dashes, parens, dots).
 */
public final class Phones {

    private Phones() {
    }

    public static String normalize(String phone) {
        if (phone == null) {
            return null;
        }
        return phone.trim().replaceAll("[\\s\\-().]", "");
    }
}
