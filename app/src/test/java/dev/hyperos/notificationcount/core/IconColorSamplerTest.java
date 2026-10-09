package dev.hyperos.notificationcount.core;

import static org.junit.Assert.*;

import java.util.Arrays;
import org.junit.Test;

public class IconColorSamplerTest {
    @Test public void theMeasuredWechatGreenSurvivesWhiteForegroundAndTransparency() {
        int[] pixels = new int[4096];
        Arrays.fill(pixels, 0, 3000, 0xff03d769);
        Arrays.fill(pixels, 3000, 3800, 0xffffffff);
        assertEquals(Integer.valueOf(0xff03d769), IconColorSampler.candidate(pixels));
    }

    @Test public void monochromeAndInvisibleImagesHaveNoInventedAccent() {
        assertNull(IconColorSampler.candidate(new int[4096]));
        int[] pixels = new int[4096];
        Arrays.fill(pixels, 0xfffefefe);
        pixels[0] = 0xff0d0d0d;
        assertNull(IconColorSampler.candidate(pixels));
        Arrays.fill(pixels, 0x9f03d769);
        assertNull(IconColorSampler.candidate(pixels));
    }

    @Test public void tinyColorArtifactsAreRejectedButTheMtProbeCandidateIsKept() {
        int[] pixels = new int[4096];
        Arrays.fill(pixels, 0xfff7f9fc);
        Arrays.fill(pixels, 0, 7, 0xffecb82b);
        assertNull(IconColorSampler.candidate(pixels));
        Arrays.fill(pixels, 0, 81, 0xffecb82b);
        assertNull(IconColorSampler.candidate(pixels));
        Arrays.fill(pixels, 0, 194, 0xffecb82b);
        assertEquals(Integer.valueOf(0xffecb82b), IconColorSampler.candidate(pixels));
    }

    @Test public void selectsTheLargestColorBucketAndAveragesItsActualPixels() {
        int[] pixels = new int[100];
        Arrays.fill(pixels, 0, 60, 0xff429cf5);
        Arrays.fill(pixels, 60, 90, 0xff439df6);
        Arrays.fill(pixels, 90, 100, 0xffff2442);
        assertEquals(Integer.valueOf(0xff429cf5), IconColorSampler.candidate(pixels));
    }
}
