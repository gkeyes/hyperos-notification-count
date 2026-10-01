package dev.hyperos.notificationcount.settings;

import static org.junit.Assert.assertEquals;

import dev.hyperos.notificationcount.core.NotificationType;
import org.junit.Test;

public class FilterPreferencesTest {
    @Test
    public void aMissingSettingLeavesAllFiltersOff() {
        SettingsTestDoubles.OptimisticPreferences preferences =
                new SettingsTestDoubles.OptimisticPreferences();
        assertEquals(0, FilterPreferences.read(preferences.preferences));
        assertEquals(0, preferences.committedMasks.size());
    }

    @Test
    public void unknownSavedBitsDoNotEnableFiltersOrRewriteTheStoredValue() {
        int savedMask = NotificationType.FOLDED.bit | NotificationType.MEDIA.bit | (1 << 27);
        SettingsTestDoubles.OptimisticPreferences preferences =
                new SettingsTestDoubles.OptimisticPreferences(savedMask);
        assertEquals(NotificationType.FOLDED.bit | NotificationType.MEDIA.bit,
                FilterPreferences.read(preferences.preferences));
        assertEquals(savedMask, preferences.persistedMask());
        assertEquals(0, preferences.committedMasks.size());
    }
}
