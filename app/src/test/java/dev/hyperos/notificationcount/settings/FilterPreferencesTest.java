package dev.hyperos.notificationcount.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import dev.hyperos.notificationcount.core.NotificationType;
import org.junit.Test;

public class FilterPreferencesTest {
    @Test public void oldConfigurationsKeepPersistentColorAndDefaultToFiveSeconds() {
        SettingsTestDoubles.OptimisticPreferences remote = new SettingsTestDoubles.OptimisticPreferences();
        assertFalse(FilterPreferences.readTemporaryColor(remote.preferences));
        assertEquals(5, FilterPreferences.readColorDuration(remote.preferences));
        assertEquals(0, remote.committedMasks.size());
    }

    @Test public void onlyTheFiveOfferedDurationsAreAcceptedAndUnknownValuesAreNotRewritten() {
        SettingsTestDoubles.OptimisticPreferences remote = new SettingsTestDoubles.OptimisticPreferences();
        for (int duration : new int[]{1,3,5,10,15}) {
            remote.preferences.edit().putInt(FilterPreferences.ICON_COLOR_DURATION, duration).commit();
            assertEquals(duration, FilterPreferences.readColorDuration(remote.preferences));
        }
        remote.preferences.edit().putInt(FilterPreferences.ICON_COLOR_DURATION, 999).commit();
        assertEquals(5, FilterPreferences.readColorDuration(remote.preferences));
        assertEquals(999, remote.preferences.getInt(FilterPreferences.ICON_COLOR_DURATION, 0));
    }

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
