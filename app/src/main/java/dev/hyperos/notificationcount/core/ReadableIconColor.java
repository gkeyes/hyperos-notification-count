package dev.hyperos.notificationcount.core;

/** Preserve the sampled hue, adjusting brightness against the native light/dark icon decision. */
public final class ReadableIconColor {
    private static final double MIN_CONTRAST = 4.5;
    private ReadableIconColor() { }

    public static int forSystemTint(Integer candidate, int systemTint) {
        if (candidate == null) return systemTint;
        int background = luminance(systemTint) < .5 ? 0xffffffff : 0xff000000;
        return (onBackground(candidate, background) & 0x00ffffff) | (systemTint & 0xff000000);
    }

    public static int onBackground(int source, int background) {
        int opaque = source | 0xff000000;
        if (contrast(opaque, background) >= MIN_CONTRAST) return opaque;
        int target = luminance(background) > .5 ? 0 : 255;
        double low = 0;
        double high = 1;
        int result = target == 0 ? 0xff000000 : 0xffffffff;
        for (int step = 0; step < 12; step++) {
            double amount = (low + high) / 2;
            int adjusted = mix(opaque, target, amount);
            if (contrast(adjusted, background) >= MIN_CONTRAST) {
                result = adjusted;
                high = amount;
            } else low = amount;
        }
        return result;
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
