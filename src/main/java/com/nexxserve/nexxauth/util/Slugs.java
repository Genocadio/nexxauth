package com.nexxserve.nexxauth.util;

import java.util.Locale;
import java.util.function.Predicate;

/**
 * Slug derivation and uniqueness helpers shared by every slug-bearing entity
 * (platforms, organisations).
 */
public final class Slugs {

    /** Column length for slugs in the schema. */
    private static final int MAX_LENGTH = 100;
    /** Headroom left for a numeric suffix in {@link #uniqueSlug}. */
    private static final int UNIQUE_BASE_LENGTH = MAX_LENGTH - 3;

    private Slugs() {
    }

    /** Derives a URL-safe slug from a display name. */
    public static String slugify(String name) {
        String slug = name.toLowerCase(Locale.ROOT)
                .trim()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        if (slug.length() > MAX_LENGTH) {
            slug = slug.substring(0, MAX_LENGTH).replaceAll("-+$", "");
        }
        return slug.isEmpty() ? "platform" : slug;
    }

    /**
     * Returns {@code baseSlug} when free, otherwise appends the next numeric
     * suffix ({@code -2}, {@code -3}, ...). The base is trimmed so candidates
     * never exceed the column length, and the caller decides what counts as
     * taken (platform-wide, per-platform, ...).
     */
    public static String uniqueSlug(String baseSlug, Predicate<String> taken) {
        String base = baseSlug.length() > UNIQUE_BASE_LENGTH
                ? baseSlug.substring(0, UNIQUE_BASE_LENGTH).replaceAll("-+$", "")
                : baseSlug;
        if (base.isEmpty()) {
            base = "platform";
        }
        String candidate = base;
        int suffix = 2;
        while (taken.test(candidate)) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }
}
