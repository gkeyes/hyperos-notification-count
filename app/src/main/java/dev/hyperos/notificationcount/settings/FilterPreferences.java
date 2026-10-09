package dev.hyperos.notificationcount.settings;

import android.content.SharedPreferences;
import java.util.List;

import dev.hyperos.notificationcount.core.NotificationType;

/** Atomic filter/color settings shared through the framework, never a world-readable local file. */
public final class FilterPreferences {
    public static final String GROUP = "notification_filters";
    public static final String EXCLUDED_MASK = "excluded_types";
    public static final String ICON_COLOR_ENABLED = "icon_color_enabled";
    public static final String ICON_COLOR_TEMPORARY = "icon_color_temporary";
    public static final String ICON_COLOR_DURATION = "icon_color_duration_seconds";
    public static final int DEFAULT_COLOR_DURATION = 5;
    public static final List<Integer> COLOR_DURATIONS = List.of(1, 3, 5, 10, 15);
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

    public static boolean readIconColor(SharedPreferences preferences) {
        return preferences.getBoolean(ICON_COLOR_ENABLED, false);
    }

    public static boolean readTemporaryColor(SharedPreferences preferences) {
        return preferences.getBoolean(ICON_COLOR_TEMPORARY, false);
    }

    public static int readColorDuration(SharedPreferences preferences) {
        return normalizeColorDuration(preferences.getInt(ICON_COLOR_DURATION, DEFAULT_COLOR_DURATION));
    }

    public static int normalizeColorDuration(int seconds) {
        return COLOR_DURATIONS.contains(seconds) ? seconds : DEFAULT_COLOR_DURATION;
    }
}
