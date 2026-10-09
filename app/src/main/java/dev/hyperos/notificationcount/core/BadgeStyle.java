package dev.hyperos.notificationcount.core;

import dev.hyperos.notificationcount.settings.FilterPreferences;

/** Badge display options chosen in settings; immutable so the renderer can compare snapshots. */
public final class BadgeStyle {
    public static final BadgeStyle DEFAULT = new BadgeStyle(FilterPreferences.DIGIT_COLOR_AUTO,
            FilterPreferences.DEFAULT_BADGE_CONTRAST, FilterPreferences.WEIGHT_NORMAL);

    public final int digitColor;
    public final int contrastTenths;
    public final int weight;

    public BadgeStyle(int digitColor, int contrastTenths, int weight) {
        this.digitColor = FilterPreferences.normalizeDigitColor(digitColor);
        this.contrastTenths = FilterPreferences.normalizeBadgeContrast(contrastTenths);
        this.weight = FilterPreferences.normalizeDigitWeight(weight);
    }

    /** Badge color: the native tint, or the sampled color adjusted to this style's contrast. */
    public int badgeColor(Integer candidate, int systemTint) {
        return ReadableIconColor.forSystemTint(candidate, systemTint, contrastTenths / 10.0);
    }

    /** Solid digit color for a colored badge; null keeps the transparent knockout. */
    public Integer glyphColor(Integer candidate, int badge) {
        if (candidate == null) return null;
        int alpha = badge & 0xff000000;
        if (digitColor == FilterPreferences.DIGIT_COLOR_WHITE) return 0x00ffffff | alpha;
        if (digitColor == FilterPreferences.DIGIT_COLOR_BLACK) return alpha;
        return ReadableIconColor.glyphOn(badge);
    }

    @Override public boolean equals(Object other) {
        if (!(other instanceof BadgeStyle)) return false;
        BadgeStyle style = (BadgeStyle) other;
        return digitColor == style.digitColor && contrastTenths == style.contrastTenths
                && weight == style.weight;
    }

    @Override public int hashCode() {
        return (digitColor * 31 + contrastTenths) * 31 + weight;
    }
}
