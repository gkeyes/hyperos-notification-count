package dev.hyperos.notificationcount.core;

import static org.junit.Assert.*;

import dev.hyperos.notificationcount.settings.FilterPreferences;
import org.junit.Test;

public class BadgeStyleTest {
    private static final int MAX = FilterPreferences.DIGIT_CONTRAST_BLACK_WHITE;
    private static final int HUE = FilterPreferences.DIGIT_CONTRAST_HUE;
    private static final int BOOST = FilterPreferences.DIGIT_CONTRAST_BOOST;

    @Test public void monochromeBadgesKeepTheNativeTintAndKnockout() {
        for (int digit : FilterPreferences.DIGIT_COLORS) {
            BadgeStyle style = new BadgeStyle(digit, 70, FilterPreferences.WEIGHT_BOLD, MAX);
            assertEquals(0xffffffff, style.badgeColor(null, 0xffffffff));
            assertNull(style.glyphColor(null, 0xffffffff));
        }
    }

    @Test public void digitColorChoiceIsHonouredAndKeepsBadgeAlpha() {
        int badge = 0x88f5d547;
        assertEquals(0x88ffffff, (int) new BadgeStyle(FilterPreferences.DIGIT_COLOR_WHITE, 45, 0, MAX).glyphColor(1, badge));
        assertEquals(0x88000000, (int) new BadgeStyle(FilterPreferences.DIGIT_COLOR_BLACK, 45, 0, MAX).glyphColor(1, badge));
        assertNull(new BadgeStyle(FilterPreferences.DIGIT_COLOR_TRANSPARENT, 45, 0, MAX).glyphColor(1, badge));
        assertEquals(ReadableIconColor.glyphOn(badge),
                (int) new BadgeStyle(FilterPreferences.DIGIT_COLOR_AUTO, 45, 0, MAX).glyphColor(1, badge));
    }

    @Test public void contrastLevelsAreMetAgainstTheInferredBackground() {
        int source = 0xff03d769;
        for (int tenths : FilterPreferences.BADGE_CONTRASTS) {
            BadgeStyle style = new BadgeStyle(0, tenths, 0, MAX);
            int onDark = style.badgeColor(source, 0xffffffff);
            int onLight = style.badgeColor(source, 0xff000000);
            assertTrue(ReadableIconColor.contrast(onDark, 0xff000000) >= tenths / 10.0);
            assertTrue(ReadableIconColor.contrast(onLight, 0xffffffff) >= tenths / 10.0);
        }
        // Higher settings push the color further from the original on a light bar.
        int soft = new BadgeStyle(0, 30, 0, MAX).badgeColor(source, 0xff000000);
        int high = new BadgeStyle(0, 70, 0, MAX).badgeColor(source, 0xff000000);
        assertTrue(ReadableIconColor.contrast(high, 0xffffffff) > ReadableIconColor.contrast(soft, 0xffffffff));
    }

    @Test public void invalidValuesNormalizeAndEqualityFollowsValues() {
        BadgeStyle odd = new BadgeStyle(9, 12, -1, 33);
        assertEquals(BadgeStyle.DEFAULT, odd);
        assertEquals(BadgeStyle.DEFAULT.hashCode(), odd.hashCode());
        assertNotEquals(BadgeStyle.DEFAULT, new BadgeStyle(0, 45, FilterPreferences.WEIGHT_MEDIUM, MAX));
        assertNotEquals(BadgeStyle.DEFAULT, new BadgeStyle(0, 45, 0, HUE));
    }

    @Test public void hueAndBlackWhiteDigitsLeaveTheBadgeAlone() {
        int badge = 0xff3c874a; // the medium green from the user's screenshot
        assertEquals(0xffffffff, (int) new BadgeStyle(0, 45, 0, MAX).glyphColor(1, badge));
        int hue = new BadgeStyle(0, 45, 0, HUE).glyphColor(1, badge);
        assertTrue(ReadableIconColor.lightnessContrast(hue, badge) >= ReadableIconColor.HUE_LC);
        // Alpha follows the badge so the digit fades with the disc.
        assertEquals(0x88, new BadgeStyle(0, 45, 0, HUE).glyphColor(1, 0x883c874a) >>> 24);
        // Forced colors ignore the automatic contrast level.
        assertEquals(0xff000000, (int) new BadgeStyle(FilterPreferences.DIGIT_COLOR_BLACK, 45, 0, HUE).glyphColor(1, badge));
        for (int tint : new int[]{0xffffffff, 0xff000000}) {
            assertEquals(new BadgeStyle(0, 45, 0, MAX).badgeColor(badge, tint),
                    new BadgeStyle(0, 45, 0, HUE).badgeColor(badge, tint));
        }
    }

    @Test public void boostedDigitsBeatBlackOrWhiteAndKeepTheBadgeColored() {
        int[] sources = {0xff3c874a, 0xff07c160, 0xfff44336, 0xff2196f3, 0xff009688, 0xffffeb3b, 0xffff00ff, 0xff777777};
        for (int tenths : FilterPreferences.BADGE_CONTRASTS) {
            for (int tint : new int[]{0xffffffff, 0xff000000}) {
                int bar = tint == 0xffffffff ? 0xff000000 : 0xffffffff;
                for (int source : sources) {
                    BadgeStyle plain = new BadgeStyle(0, tenths, 0, MAX);
                    BadgeStyle boost = new BadgeStyle(0, tenths, 0, BOOST);
                    int plainBadge = plain.badgeColor(source, tint);
                    int boostBadge = boost.badgeColor(source, tint);
                    int plainDigit = plain.glyphColor(source, plainBadge);
                    int boostDigit = boost.glyphColor(source, boostBadge);
                    double boosted = ReadableIconColor.lightnessContrast(boostDigit, boostBadge);
                    assertTrue(boosted >= ReadableIconColor.BOOST_LC);
                    assertTrue(boosted >= ReadableIconColor.lightnessContrast(plainDigit, plainBadge));
                    assertTrue(boostDigit == 0xffffffff || boostDigit == 0xff000000);
                    // Light bar: darkening only adds contrast. Dark bar: never washed out to near
                    // white; a badge is either left alone or darkened under a white digit.
                    if (bar == 0xffffffff) assertTrue(ReadableIconColor.contrast(boostBadge, bar) >= tenths / 10.0);
                    else assertTrue(boostBadge == plainBadge || boostDigit == 0xffffffff);
                }
            }
        }
        // The screenshot green turns darker green under a white digit on either bar.
        for (int tint : new int[]{0xffffffff, 0xff000000}) {
            int green = new BadgeStyle(0, 45, 0, BOOST).badgeColor(0xff3c874a, tint);
            assertEquals(0xffffffff, (int) new BadgeStyle(0, 45, 0, BOOST).glyphColor(1, green));
            int r = (green >>> 16) & 255, g = (green >>> 8) & 255, b = green & 255;
            assertTrue(g > r + 30 && g > b + 30);
        }
        // A light badge that already reads well with black stays as it is on a dark bar.
        assertEquals(0xffffeb3b, new BadgeStyle(0, 45, 0, BOOST).badgeColor(0xffffeb3b, 0xffffffff));
        // Badge alpha is kept, and forced digit colors leave the badge untouched.
        assertEquals(0x80, ReadableIconColor.boostedForDigits(0x803c874a, 90) >>> 24);
        assertEquals(new BadgeStyle(FilterPreferences.DIGIT_COLOR_WHITE, 45, 0, MAX).badgeColor(0xff3c874a, 0xff000000),
                new BadgeStyle(FilterPreferences.DIGIT_COLOR_WHITE, 45, 0, BOOST).badgeColor(0xff3c874a, 0xff000000));
        assertEquals(0xffffffff, new BadgeStyle(0, 45, 0, BOOST).badgeColor(null, 0xffffffff));
    }
}
