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
    /** Digit color on a colored badge: automatic black/white, or forced. */
    public static final String DIGIT_COLOR = "badge_digit_color";
    public static final int DIGIT_COLOR_AUTO = 0;
    public static final int DIGIT_COLOR_WHITE = 1;
    public static final int DIGIT_COLOR_BLACK = 2;
    /** Keep the digit knocked out so the status bar shows through, as in monochrome mode. */
    public static final int DIGIT_COLOR_TRANSPARENT = 3;
    public static final List<Integer> DIGIT_COLORS = List.of(
            DIGIT_COLOR_AUTO, DIGIT_COLOR_WHITE, DIGIT_COLOR_BLACK, DIGIT_COLOR_TRANSPARENT);
    /** Minimum contrast of a colored badge against the status bar, in tenths (4.5 = 45). */
    public static final String BADGE_CONTRAST = "badge_contrast_tenths";
    public static final int DEFAULT_BADGE_CONTRAST = 45;
    public static final List<Integer> BADGE_CONTRASTS = List.of(30, 45, 70);
    /** Digit weight: index into the normal / medium / bold vector sets. */
    public static final String DIGIT_WEIGHT = "badge_digit_weight";
    public static final int WEIGHT_NORMAL = 0;
    public static final int WEIGHT_MEDIUM = 1;
    public static final int WEIGHT_BOLD = 2;
    public static final List<Integer> DIGIT_WEIGHTS = List.of(WEIGHT_NORMAL, WEIGHT_MEDIUM, WEIGHT_BOLD);
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

    public static int readDigitColor(SharedPreferences preferences) {
        return normalizeDigitColor(preferences.getInt(DIGIT_COLOR, DIGIT_COLOR_AUTO));
    }

    public static int normalizeDigitColor(int value) {
        return DIGIT_COLORS.contains(value) ? value : DIGIT_COLOR_AUTO;
    }

    public static int readBadgeContrast(SharedPreferences preferences) {
        return normalizeBadgeContrast(preferences.getInt(BADGE_CONTRAST, DEFAULT_BADGE_CONTRAST));
    }

    public static int normalizeBadgeContrast(int tenths) {
        return BADGE_CONTRASTS.contains(tenths) ? tenths : DEFAULT_BADGE_CONTRAST;
    }

    public static int readDigitWeight(SharedPreferences preferences) {
        return normalizeDigitWeight(preferences.getInt(DIGIT_WEIGHT, WEIGHT_NORMAL));
    }

    public static int normalizeDigitWeight(int weight) {
        return DIGIT_WEIGHTS.contains(weight) ? weight : WEIGHT_NORMAL;
    }
}
