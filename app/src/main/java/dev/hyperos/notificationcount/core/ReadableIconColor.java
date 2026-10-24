package dev.hyperos.notificationcount.core;

/** Preserve the sampled hue, adjusting brightness against the native light/dark icon decision. */
public final class ReadableIconColor {
    private static final double MIN_CONTRAST = 4.5;
    private ReadableIconColor() { }

    public static int forSystemTint(Integer candidate, int systemTint) {
        return forSystemTint(candidate, systemTint, MIN_CONTRAST);
    }

    /** As above with a caller-chosen minimum contrast against the inferred background. */
    public static int forSystemTint(Integer candidate, int systemTint, double minContrast) {
        if (candidate == null) return systemTint;
        int background = luminance(systemTint) < .5 ? 0xffffffff : 0xff000000;
        return (onBackground(candidate, background, minContrast) & 0x00ffffff) | (systemTint & 0xff000000);
    }

    public static int onBackground(int source, int background) {
        return onBackground(source, background, MIN_CONTRAST);
    }

    public static int onBackground(int source, int background, double minContrast) {
        int opaque = source | 0xff000000;
        if (contrast(opaque, background) >= minContrast) return opaque;
        int target = luminance(background) > .5 ? 0 : 255;
        double low = 0;
        double high = 1;
        int result = target == 0 ? 0xff000000 : 0xffffffff;
        for (int step = 0; step < 12; step++) {
            double amount = (low + high) / 2;
            int adjusted = mix(opaque, target, amount);
            if (contrast(adjusted, background) >= minContrast) {
                result = adjusted;
                high = amount;
            } else low = amount;
        }
        return result;
    }

    /**
     * Black or white for digits drawn on a colored badge, whichever reads better.
     * Judged by APCA lightness contrast rather than the WCAG ratio: on mid-tone colors such as
     * a medium green the WCAG ratio slightly favours black, while white is clearly more legible.
     * The badge's alpha is kept so the digit fades together with the disc.
     */
    public static int glyphOn(int badge) {
        int opaque = badge | 0xff000000;
        int glyph = lightnessContrast(0xffffffff, opaque) >= lightnessContrast(0xff000000, opaque)
                ? 0xffffff : 0x000000;
        return glyph | (badge & 0xff000000);
    }

    /**
     * A digit in the badge's own hue: the badge mixed toward the black or white that
     * {@link #glyphOn} would pick, just far enough to reach {@code minLc} APCA lightness
     * contrast against the badge. Falls back to that pure black or white when out of reach.
     */
    public static int tintedGlyphOn(int badge, double minLc) {
        int opaque = badge | 0xff000000;
        int extreme = glyphOn(opaque) | 0xff000000;
        int target = extreme == 0xffffffff ? 255 : 0;
        int result = extreme;
        if (lightnessContrast(extreme, opaque) >= minLc) {
            double low = 0;
            double high = 1;
            for (int step = 0; step < 12; step++) {
                double amount = (low + high) / 2;
                int adjusted = mix(opaque, target, amount);
                if (lightnessContrast(adjusted, opaque) >= minLc) {
                    result = adjusted;
                    high = amount;
                } else low = amount;
            }
        }
        return (result & 0x00ffffff) | (badge & 0xff000000);
    }

    /**
     * APCA (0.0.98G) lightness contrast of {@code text} on {@code background}, as an absolute
     * Lc value from 0 to about 106; Lc 60 suits readable content text, Lc 75 body text.
     */
    public static double lightnessContrast(int text, int background) {
        double textY = apcaLuminance(text);
        double backgroundY = apcaLuminance(background);
        if (Math.abs(backgroundY - textY) < 0.0005) return 0;
        double lc;
        if (backgroundY > textY) {
            double s = (Math.pow(backgroundY, 0.56) - Math.pow(textY, 0.57)) * 1.14;
            lc = s < 0.1 ? 0 : s - 0.027;
        } else {
            double s = (Math.pow(backgroundY, 0.65) - Math.pow(textY, 0.62)) * 1.14;
            lc = s > -0.1 ? 0 : s + 0.027;
        }
        return Math.abs(lc) * 100;
    }

    private static double apcaLuminance(int color) {
        double y = 0.2126729 * Math.pow(((color >>> 16) & 255) / 255.0, 2.4)
                + 0.7151522 * Math.pow(((color >>> 8) & 255) / 255.0, 2.4)
                + 0.0721750 * Math.pow((color & 255) / 255.0, 2.4);
        return y < 0.022 ? y + Math.pow(0.022 - y, 1.414) : y;
    }

    public static double contrast(int first, int second) {
        double a = luminance(first);
        double b = luminance(second);
        return (Math.max(a, b) + .05) / (Math.min(a, b) + .05);
    }

    private static int mix(int source, int target, double amount) {
        int r = (int) Math.round(((source >>> 16) & 255) * (1-amount) + target * amount);
        int g = (int) Math.round(((source >>> 8) & 255) * (1-amount) + target * amount);
        int b = (int) Math.round((source & 255) * (1-amount) + target * amount);
        return 0xff000000 | (r << 16) | (g << 8) | b;
    }

    private static double luminance(int color) {
        return .2126 * linear((color >>> 16) & 255)
                + .7152 * linear((color >>> 8) & 255) + .0722 * linear(color & 255);
    }

    private static double linear(int channel) {
        double value = channel / 255.0;
        return value <= .04045 ? value / 12.92 : Math.pow((value + .055) / 1.055, 2.4);
    }
}
