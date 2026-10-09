package dev.hyperos.tools;

import java.util.Arrays;

public final class ColorSamplerTest {
    private static int checks;

    public static void main(String[] args) {
        ColorSampler.Result empty = ColorSampler.sample(new int[64]);
        expect("EMPTY".equals(empty.status()) && empty.dominant == null, "transparent icon");

        int[] green = new int[4096];
        Arrays.fill(green, 0xff07c160);
        ColorSampler.Result solid = ColorSampler.sample(green);
        expect("#07C160".equals(ColorSampler.hex(solid.candidate)), "solid app green");
        expect(solid.visible == 4096 && solid.colorfulFraction() == 1, "coverage");

        int[] whiteLogo = new int[4096];
        Arrays.fill(whiteLogo, 0xffffffff);
        Arrays.fill(whiteLogo, 0, 1000, 0xff07c160);
        ColorSampler.Result mixed = ColorSampler.sample(whiteLogo);
        expect("#FFFFFF".equals(ColorSampler.hex(mixed.dominant)), "white overall majority");
        expect("#07C160".equals(ColorSampler.hex(mixed.candidate)), "colored accent survives white majority");

        for (int neutral : new int[] {0xff000000, 0xff0d0d0d, 0xff808080, 0xffffffff}) {
            int[] image = new int[256];
            Arrays.fill(image, neutral);
            ColorSampler.Result result = ColorSampler.sample(image);
            expect("NEUTRAL".equals(result.status()) && result.candidate == null,
                    "neutral icon " + ColorSampler.hex(neutral));
            expect(result.dominant != null && result.visible == 256, "neutral is not missing");
        }

        int[] translucent = new int[100];
        Arrays.fill(translucent, 0x7fff0000);
        ColorSampler.Result skipped = ColorSampler.sample(translucent);
        expect(skipped.visible == 0 && skipped.candidate == null, "low alpha edge noise");

        int[] almostWhite = new int[1000];
        Arrays.fill(almostWhite, 0xffffffff);
        Arrays.fill(almostWhite, 0, 10, 0xffff0000);
        ColorSampler.Result sparse = ColorSampler.sample(almostWhite);
        expect(sparse.chromatic == 10 && sparse.candidate == null, "tiny colored patch does not dominate");

        int[] dim = new int[64];
        Arrays.fill(dim, 0xff101800);
        expect(ColorSampler.sample(dim).candidate == null, "near-black saturation rejected");

        int[] average = new int[64];
        Arrays.fill(average, 0xff102030);
        Arrays.fill(average, 0, 32, 0xff122232);
        expect("#112131".equals(ColorSampler.hex(ColorSampler.sample(average).dominant)),
                "dominant reports actual bucket mean");
        expect("NONE".equals(ColorSampler.hex(null)), "missing is not black");
        System.out.println("PASS ColorSampler: " + checks + " assertions");
    }

    private static void expect(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
