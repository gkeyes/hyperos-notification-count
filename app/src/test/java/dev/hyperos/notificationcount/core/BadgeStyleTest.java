package dev.hyperos.notificationcount.core;

import static org.junit.Assert.*;

import dev.hyperos.notificationcount.settings.FilterPreferences;
import org.junit.Test;

public class BadgeStyleTest {
    @Test public void monochromeBadgesKeepTheNativeTintAndKnockout() {
        for (int digit : FilterPreferences.DIGIT_COLORS) {
            BadgeStyle style = new BadgeStyle(digit, 70, FilterPreferences.WEIGHT_BOLD);
            assertEquals(0xffffffff, style.badgeColor(null, 0xffffffff));
            assertNull(style.glyphColor(null, 0xffffffff));
        }
    }

    @Test public void digitColorChoiceIsHonouredAndKeepsBadgeAlpha() {
        int badge = 0x88f5d547;
        assertEquals(0x88ffffff, (int) new BadgeStyle(FilterPreferences.DIGIT_COLOR_WHITE, 45, 0).glyphColor(1, badge));
        assertEquals(0x88000000, (int) new BadgeStyle(FilterPreferences.DIGIT_COLOR_BLACK, 45, 0).glyphColor(1, badge));
        assertNull(new BadgeStyle(FilterPreferences.DIGIT_COLOR_TRANSPARENT, 45, 0).glyphColor(1, badge));
        assertEquals(ReadableIconColor.glyphOn(badge),
                (int) new BadgeStyle(FilterPreferences.DIGIT_COLOR_AUTO, 45, 0).glyphColor(1, badge));
    }

    @Test public void contrastLevelsAreMetAgainstTheInferredBackground() {
        int source = 0xff03d769;
        for (int tenths : FilterPreferences.BADGE_CONTRASTS) {
            BadgeStyle style = new BadgeStyle(0, tenths, 0);
            int onDark = style.badgeColor(source, 0xffffffff);
            int onLight = style.badgeColor(source, 0xff000000);
            assertTrue(ReadableIconColor.contrast(onDark, 0xff000000) >= tenths / 10.0);
            assertTrue(ReadableIconColor.contrast(onLight, 0xffffffff) >= tenths / 10.0);
        }
        // Higher settings push the color further from the original on a light bar.
        int soft = new BadgeStyle(0, 30, 0).badgeColor(source, 0xff000000);
        int high = new BadgeStyle(0, 70, 0).badgeColor(source, 0xff000000);
        assertTrue(ReadableIconColor.contrast(high, 0xffffffff) > ReadableIconColor.contrast(soft, 0xffffffff));
    }

    @Test public void invalidValuesNormalizeAndEqualityFollowsValues() {
        BadgeStyle odd = new BadgeStyle(9, 12, -1);
        assertEquals(BadgeStyle.DEFAULT, odd);
        assertEquals(BadgeStyle.DEFAULT.hashCode(), odd.hashCode());
        assertNotEquals(BadgeStyle.DEFAULT, new BadgeStyle(0, 45, FilterPreferences.WEIGHT_MEDIUM));
    }
}
