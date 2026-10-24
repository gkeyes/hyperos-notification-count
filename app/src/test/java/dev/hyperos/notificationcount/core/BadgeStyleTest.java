package dev.hyperos.notificationcount.core;

import static org.junit.Assert.*;

import dev.hyperos.notificationcount.settings.FilterPreferences;
import org.junit.Test;

public class BadgeStyleTest {
    private static final int MAX = FilterPreferences.DIGIT_CONTRAST_MAX;

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
        assertNotEquals(BadgeStyle.DEFAULT, new BadgeStyle(0, 45, 0, 60));
    }

    @Test public void automaticDigitContrastLevelsKeepTheBadgeHueUntilTheMaximum() {
        int badge = 0xff3c874a; // the medium green from the user's screenshot
        assertEquals(0xffffffff, (int) new BadgeStyle(0, 45, 0, MAX).glyphColor(1, badge));
        int standard = new BadgeStyle(0, 45, 0, 60).glyphColor(1, badge);
        int high = new BadgeStyle(0, 45, 0, 75).glyphColor(1, badge);
        assertTrue(ReadableIconColor.lightnessContrast(standard, badge) >= 60);
        assertTrue(ReadableIconColor.lightnessContrast(high, badge) >= 75);
        assertNotEquals(0xffffffff, standard);
        assertTrue(ReadableIconColor.lightnessContrast(high, badge)
                >= ReadableIconColor.lightnessContrast(standard, badge));
        // Alpha follows the badge so the digit fades with the disc.
        assertEquals(0x88, new BadgeStyle(0, 45, 0, 60).glyphColor(1, 0x883c874a) >>> 24);
        // Forced colors ignore the automatic contrast level.
        assertEquals(0xff000000, (int) new BadgeStyle(FilterPreferences.DIGIT_COLOR_BLACK, 45, 0, 60).glyphColor(1, badge));
    }
}
