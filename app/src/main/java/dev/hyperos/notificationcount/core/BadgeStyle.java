package dev.hyperos.notificationcount.core;

import dev.hyperos.notificationcount.settings.FilterPreferences;

/** Badge display options chosen in settings; immutable so the renderer can compare snapshots. */
public final class BadgeStyle {
    public static final BadgeStyle DEFAULT = new BadgeStyle(FilterPreferences.DIGIT_COLOR_AUTO,
            FilterPreferences.DEFAULT_BADGE_CONTRAST, FilterPreferences.WEIGHT_NORMAL,
            FilterPreferences.DEFAULT_DIGIT_CONTRAST);

    public final int digitColor;
    public final int contrastTenths;
    public final int weight;
    /** How automatic digits stand out; see FilterPreferences.DIGIT_CONTRAST. */
    public final int digitContrast;

    public BadgeStyle(int digitColor, int contrastTenths, int weight, int digitContrast) {
        this.digitColor = FilterPreferences.normalizeDigitColor(digitColor);
        this.contrastTenths = FilterPreferences.normalizeBadgeContrast(contrastTenths);
        this.weight = FilterPreferences.normalizeDigitWeight(weight);
        this.digitContrast = FilterPreferences.normalizeDigitContrast(digitContrast);
    }

    /**
     * Badge color: the native tint, or the sampled color adjusted to this style's contrast.
     * With boosted automatic digits the result is then darkened, when needed, until a white
     * digit reaches {@link ReadableIconColor#BOOST_LC}; that may take it below the chosen
     * contrast against a dark bar, which keeps the hue instead of washing it out.
     */
    public int badgeColor(Integer candidate, int systemTint) {
        int badge = ReadableIconColor.forSystemTint(candidate, systemTint, contrastTenths / 10.0);
        if (candidate == null || digitColor != FilterPreferences.DIGIT_COLOR_AUTO
                || digitContrast != FilterPreferences.DIGIT_CONTRAST_BOOST) return badge;
        return ReadableIconColor.boostedForDigits(badge, ReadableIconColor.BOOST_LC);
    }

    /** Solid digit color for a colored badge; null keeps the transparent knockout. */
    public Integer glyphColor(Integer candidate, int badge) {
        if (candidate == null || digitColor == FilterPreferences.DIGIT_COLOR_TRANSPARENT) return null;
        int alpha = badge & 0xff000000;
        if (digitColor == FilterPreferences.DIGIT_COLOR_WHITE) return 0x00ffffff | alpha;
        if (digitColor == FilterPreferences.DIGIT_COLOR_BLACK) return alpha;
        if (digitContrast == FilterPreferences.DIGIT_CONTRAST_HUE) {
            return ReadableIconColor.tintedGlyphOn(badge, ReadableIconColor.HUE_LC);
        }
        return ReadableIconColor.glyphOn(badge);
    }

    @Override public boolean equals(Object other) {
        if (!(other instanceof BadgeStyle)) return false;
        BadgeStyle style = (BadgeStyle) other;
        return digitColor == style.digitColor && contrastTenths == style.contrastTenths
                && weight == style.weight && digitContrast == style.digitContrast;
    }

    @Override public int hashCode() {
        return ((digitColor * 31 + contrastTenths) * 31 + weight) * 31 + digitContrast;
    }
}
