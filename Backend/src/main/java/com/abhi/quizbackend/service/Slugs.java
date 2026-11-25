package com.abhi.quizbackend.service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.function.Predicate;

/** URL slugs for tracks and topics. */
final class Slugs {

    private Slugs() {
    }

    /** "Graphs & Trees" becomes "graphs-trees". Returns the fallback if nothing is left. */
    static String slugify(String value, String fallback) {
        String slug = Normalizer.normalize(value, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");

        if (slug.length() > 80) {
            slug = slug.substring(0, 80).replaceAll("-$", "");
        }
        return slug.isEmpty() ? fallback : slug;
    }

    /** The base slug, or base-2, base-3 and so on, whichever is free first. */
    static String unique(String base, Predicate<String> taken) {
        String slug = base;
        for (int n = 2; taken.test(slug); n++) {
            slug = base + "-" + n;
        }
        return slug;
    }
}
