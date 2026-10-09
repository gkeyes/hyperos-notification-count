package dev.hyperos.notificationcount.core;

/** The probe 0.2 candidate algorithm, without its diagnostic dominant-color histogram. */
public final class IconColorSampler {
    private IconColorSampler() { }

    /** Returns opaque ARGB, or null when the icon has no sufficiently visible chromatic pixels. */
    public static Integer candidate(int[] pixels) {
        int[] count = new int[4096];
        int[] red = new int[4096];
        int[] green = new int[4096];
        int[] blue = new int[4096];
        int visible = 0;
        int chromatic = 0;
        for (int argb : pixels) {
            if ((argb >>> 24) < 160) continue;
            visible++;
            int r = (argb >>> 16) & 255;
            int g = (argb >>> 8) & 255;
            int b = argb & 255;
            int max = Math.max(r, Math.max(g, b));
            int min = Math.min(r, Math.min(g, b));
            if (max < 40 || (max - min) * 100 < max * 20) continue;
            chromatic++;
            int bin = ((r >>> 4) << 8) | ((g >>> 4) << 4) | (b >>> 4);
            count[bin]++;
            red[bin] += r;
            green[bin] += g;
            blue[bin] += b;
        }
        if (chromatic < 8 || chromatic * 100L < visible * 2L) return null;
        int best = -1;
        for (int i = 0; i < count.length; i++) {
            if (count[i] > 0 && (best < 0 || count[i] > count[best])) best = i;
        }
        return 0xff000000 | ((red[best] / count[best]) << 16)
                | ((green[best] / count[best]) << 8) | (blue[best] / count[best]);
    }
}
