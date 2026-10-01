package dev.hyperos.notificationcount.settings;

import android.content.SharedPreferences;

import dev.hyperos.notificationcount.core.NotificationType;

/** One atomic mask shared through the framework, never a world-readable local file. */
public final class FilterPreferences {
    public static final String GROUP = "notification_filters";
    public static final String EXCLUDED_MASK = "excluded_types";
    public static final int KNOWN_MASK;

    static {
        int bits = 0;
        for (NotificationType type : NotificationType.values()) bits |= type.bit;
        KNOWN_MASK = bits;
    }

    private FilterPreferences() { }

    public static int read(SharedPreferences preferences) {
        return preferences.getInt(EXCLUDED_MASK, 0) & KNOWN_MASK;
    }
}
