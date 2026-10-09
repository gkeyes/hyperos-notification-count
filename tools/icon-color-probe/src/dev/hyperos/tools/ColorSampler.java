package dev.hyperos.tools;

import java.util.Locale;

/** Small, dependency-free histogram for a diagnostic icon thumbnail. */
public final class ColorSampler {
    private ColorSampler() {}

    public static final class Result {
        public final int visible;
        public final int chromatic;
        public final Integer dominant;
        public final Integer candidate;

        Result(int visible, int chromatic, Integer dominant, Integer candidate) {
            this.visible = visible;
            this.chromatic = chromatic;
            this.dominant = dominant;
            this.candidate = candidate;
        }

        public String status() {
            return visible == 0 ? "EMPTY" : candidate == null ? "NEUTRAL" : "COLORFUL";
        }

        public double colorfulFraction() {
            return visible == 0 ? 0 : chromatic / (double) visible;
        }
    }

    public static Result sample(int[] pixels) {
        Histogram all = new Histogram();
        Histogram colorful = new Histogram();
        int visible = 0;
        int chromatic = 0;
        for (int argb : pixels) {
            if ((argb >>> 24) < 160) continue;
            int r = (argb >>> 16) & 255;
            int g = (argb >>> 8) & 255;
            int b = argb & 255;
            visible++;
            all.add(r, g, b);
            int max = Math.max(r, Math.max(g, b));
            int min = Math.min(r, Math.min(g, b));
            // Exclude near-black and weakly saturated pixels from the accent candidate.
            if (max >= 40 && (max - min) * 100 >= max * 20) {
                chromatic++;
                colorful.add(r, g, b);
            }
        }
        Integer candidate = chromatic >= 8 && chromatic * 100L >= visible * 2L
                ? colorful.peak() : null;
        return new Result(visible, chromatic, all.peak(), candidate);
    }

    public static String hex(Integer color) {
        return color == null ? "NONE" : String.format(Locale.ROOT, "#%06X", color & 0xffffff);
    }

    private static final class Histogram {
        private final int[] count = new int[4096];
        private final long[] red = new long[4096];
        private final long[] green = new long[4096];
        private final long[] blue = new long[4096];

        void add(int r, int g, int b) {
            int bin = ((r >>> 4) << 8) | ((g >>> 4) << 4) | (b >>> 4);
            count[bin]++;
            red[bin] += r;
            green[bin] += g;
            blue[bin] += b;
        }

        Integer peak() {
            int best = -1;
            for (int i = 0; i < count.length; i++) {
                if (count[i] > 0 && (best < 0 || count[i] > count[best])) best = i;
            }
            if (best < 0) return null;
            return ((int) (red[best] / count[best]) << 16)
                    | ((int) (green[best] / count[best]) << 8)
                    | (int) (blue[best] / count[best]);
        }
    }
}
