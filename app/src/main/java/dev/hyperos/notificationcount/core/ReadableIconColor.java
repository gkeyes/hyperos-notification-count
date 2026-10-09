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
     * The badge's alpha is kept so the digit fades together with the disc.
     */
    public static int glyphOn(int badge) {
        int opaque = badge | 0xff000000;
        int glyph = contrast(opaque, 0xffffffff) >= contrast(opaque, 0xff000000) ? 0xffffff : 0x000000;
        return glyph | (badge & 0xff000000);
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
