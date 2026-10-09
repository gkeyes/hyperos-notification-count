package dev.hyperos.notificationcount.core;

import static org.junit.Assert.*;
import org.junit.Test;

public class ReadableIconColorTest {
    @Test public void missingCandidatesPreserveTheExactNativeTintIncludingAlpha() {
        assertEquals(0xff000000, ReadableIconColor.forSystemTint(null, 0xff000000));
        assertEquals(0xffffffff, ReadableIconColor.forSystemTint(null, 0xffffffff));
        assertEquals(0x88abcdef, ReadableIconColor.forSystemTint(null, 0x88abcdef));
        assertEquals(0x88, ReadableIconColor.forSystemTint(0xff03d769, 0x88000000) >>> 24);
        assertEquals(0, ReadableIconColor.forSystemTint(0xff03d769, 0x00000000) >>> 24);
    }

    @Test public void measuredColorsRemainReadableInBothNativeModes() {
        int[] samples = {0xff03d769, 0xff8785fe, 0xff429cf5, 0xff17a6fa,
                0xffecb82b, 0xff283349, 0xffd8b8a6};
        for (int source : samples) {
            int light = ReadableIconColor.forSystemTint(source, 0xff000000);
            int dark = ReadableIconColor.forSystemTint(source, 0xffffffff);
            assertTrue(ReadableIconColor.contrast(light, 0xffffffff) >= 4.5);
            assertTrue(ReadableIconColor.contrast(dark, 0xff000000) >= 4.5);
            assertEquals(255, light >>> 24);
            assertEquals(255, dark >>> 24);
        }
        assertEquals(0xff03d769, ReadableIconColor.forSystemTint(0xff03d769, 0xffffffff));
        assertNotEquals(0xff03d769, ReadableIconColor.forSystemTint(0xff03d769, 0xff000000));
    }

    @Test public void contrastAdjustmentWorksAcrossTheRgbCube() {
        for (int r = 0; r <= 255; r += 51) {
            for (int g = 0; g <= 255; g += 51) {
                for (int b = 0; b <= 255; b += 51) {
                    int source = 0xff000000 | (r << 16) | (g << 8) | b;
                    for (int background : new int[]{0xff000000, 0xffffffff}) {
                        assertTrue(ReadableIconColor.contrast(
                                ReadableIconColor.onBackground(source, background), background) >= 4.5);
                    }
                }
            }
        }
    }

    @Test public void badgeDigitsPickTheMoreReadableOfBlackOrWhite() {
        assertEquals(0xffffffff, ReadableIconColor.glyphOn(0xff1f3a8a));
        assertEquals(0xff000000, ReadableIconColor.glyphOn(0xfff5d547));
        assertEquals(0x88000000, ReadableIconColor.glyphOn(0x88f5d547));
        for (int r = 0; r <= 255; r += 51) for (int g = 0; g <= 255; g += 51) for (int b = 0; b <= 255; b += 51) {
            int badge = 0xff000000 | (r << 16) | (g << 8) | b;
            int glyph = ReadableIconColor.glyphOn(badge);
            int other = glyph == 0xffffffff ? 0xff000000 : 0xffffffff;
            assertTrue(ReadableIconColor.contrast(glyph, badge) >= ReadableIconColor.contrast(other, badge));
        }
    }
}
