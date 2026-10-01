package dev.hyperos.notificationcount.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import dev.hyperos.notificationcount.R;
import dev.hyperos.notificationcount.core.NotificationType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 37, application = SettingsActivityTest.TestApplication.class)
public class SettingsActivityTest {
    public static class TestApplication extends ModuleApplication {
        private final SettingsStore settings = new SettingsStore(Runnable::run, Runnable::run);

        @Override public void onCreate() {
            // Exercise the screen without registering libxposed's real process service.
        }

        @Override public SettingsStore getSettingsStore() { return settings; }
    }

    private TestApplication application;
    private SharedPreferences persisted;
    private ActivityController<SettingsActivity> controller;
    private SettingsActivity activity;

    @Before
    public void setUp() {
        application = (TestApplication) RuntimeEnvironment.getApplication();
        persisted = application.getSharedPreferences("settings-test", Context.MODE_PRIVATE);
        persisted.edit().clear().commit();
    }

    @After
    public void tearDown() { closeActivity(); }

    @Test
    public void allFifteenFiltersHaveTheirOwnLabelsTagsAndOffDefaults() {
        openActivity();
        Map<NotificationType, Switch> switches = switches();
        assertEquals(15, switches.size());
        for (NotificationType type : NotificationType.values()) {
            Switch toggle = switches.get(type);
            assertNotNull(type.key, toggle);
            assertFalse(type.key, toggle.isChecked());
            assertFalse(type.key, toggle.isEnabled());
            int[] expected = expectedLabels(type);
            String title = activity.getString(expected[0]);
            assertEquals(type.key, title + "。" + activity.getString(expected[1]),
                    toggle.getContentDescription().toString());
            TextView label = labelFor(toggle);
            assertNotNull(type.key, label);
            assertEquals(type.key, title, label.getText().toString());
        }
        assertFalse(button(R.string.settings_reset).isEnabled());
        assertEquals(View.VISIBLE, button(R.string.settings_retry).getVisibility());
        assertTrue(hasText(R.string.settings_waiting));

        application.getSettingsStore().connect(new Object(), () -> persisted);
        assertMaskAndEnabled(0, true);
        assertEquals(View.GONE, button(R.string.settings_retry).getVisibility());
        assertTrue(hasText(R.string.settings_ready));
    }

    @Test
    public void togglingEveryFilterPersistsReopensAndResetsTogether() {
        application.getSettingsStore().connect(new Object(), () -> persisted);
        openActivity();
        int mask = 0;
        for (NotificationType type : NotificationType.values()) {
            assertTrue(type.key, switches().get(type).performClick());
            mask |= type.bit;
            assertEquals(type.key, mask, persisted.getInt(FilterPreferences.EXCLUDED_MASK, 0));
        }
        assertEquals(FilterPreferences.KNOWN_MASK, mask);
        assertTrue(hasLiteralText(activity.getString(R.string.settings_selected, 15)));
        closeActivity();
        openActivity();
        assertMaskAndEnabled(FilterPreferences.KNOWN_MASK, true);

        assertTrue(button(R.string.settings_reset).performClick());
        assertMaskAndEnabled(0, true);
        assertEquals(0, persisted.getInt(FilterPreferences.EXCLUDED_MASK, -1));
        assertFalse(button(R.string.settings_reset).isEnabled());
        assertTrue(hasLiteralText(activity.getString(R.string.settings_selected, 0)));
    }

    @Test
    public void failedSaveRestoresSwitchesDisablesEditsAndRetryKeepsTheConfirmedMask() {
        int previous = NotificationType.ONGOING_EVENT.bit;
        SettingsTestDoubles.OptimisticPreferences remote =
                new SettingsTestDoubles.OptimisticPreferences(previous);
        remote.queueCommitResults(false, false);
        application.getSettingsStore().connect(new Object(), () -> remote.preferences);
        openActivity();
        assertTrue(switches().get(NotificationType.MEDIA).performClick());
        assertMaskAndEnabled(previous, false);
        for (Switch toggle : switches().values()) {
            assertFalse(((View) toggle.getParent()).isEnabled());
        }
        assertFalse(button(R.string.settings_reset).isEnabled());
        assertTrue(hasText(R.string.settings_save_failed));
        Button retry = button(R.string.settings_retry);
        assertEquals(View.VISIBLE, retry.getVisibility());
        assertTrue(retry.isEnabled());

        assertTrue(retry.performClick());
        assertMaskAndEnabled(previous, true);
        assertEquals(previous, remote.persistedMask());
        assertEquals(3, remote.committedMasks.size());
        assertTrue(hasText(R.string.settings_ready));
    }

    @Test
    public void stoppedScreenStopsObservingAndRendersCurrentStateWhenStartedAgain() {
        application.getSettingsStore().connect(new Object(), () -> persisted);
        openActivity();
        Switch focus = switches().get(NotificationType.FOCUS);
        controller.pause().stop();
        application.getSettingsStore().setExcluded(NotificationType.FOCUS, true);
        assertFalse(focus.isChecked());
        controller.start().resume();
        assertTrue(focus.isChecked());
        assertTrue(focus.isEnabled());
    }

    @Test
    public void manifestResolvesTheManagerSettingsCategoryWithoutALauncherEntry() {
        String packageName = application.getPackageName();
        Intent settings = new Intent(Intent.ACTION_MAIN)
                .addCategory("de.robv.android.xposed.category.MODULE_SETTINGS")
                .setPackage(packageName);
        List<ResolveInfo> matches = application.getPackageManager().queryIntentActivities(settings, 0);
        assertEquals(1, matches.size());
        assertEquals(SettingsActivity.class.getName(), matches.get(0).activityInfo.name);
        assertTrue(matches.get(0).activityInfo.exported);
        Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setPackage(packageName);
        assertTrue(application.getPackageManager().queryIntentActivities(launcher, 0).isEmpty());
    }

    private void openActivity() {
        controller = Robolectric.buildActivity(SettingsActivity.class).setup();
        activity = controller.get();
    }

    private void closeActivity() {
        if (controller != null) {
            controller.pause().stop().destroy();
            controller = null;
        }
    }

    private Map<NotificationType, Switch> switches() {
        Map<NotificationType, Switch> result = new EnumMap<>(NotificationType.class);
        for (Switch toggle : views(Switch.class)) {
            assertTrue(toggle.getTag() instanceof NotificationType);
            NotificationType type = (NotificationType) toggle.getTag();
            assertFalse("Duplicate switch: " + type.key, result.containsKey(type));
            result.put(type, toggle);
        }
        return result;
    }

    private TextView labelFor(Switch toggle) {
        for (TextView candidate : views(TextView.class)) {
            if (candidate.getLabelFor() == toggle.getId()) return candidate;
        }
        return null;
    }

    private void assertMaskAndEnabled(int mask, boolean enabled) {
        Map<NotificationType, Switch> toggles = switches();
        assertEquals(15, toggles.size());
        for (NotificationType type : NotificationType.values()) {
            assertEquals(type.key, (mask & type.bit) != 0, toggles.get(type).isChecked());
            assertEquals(type.key, enabled, toggles.get(type).isEnabled());
        }
    }

    private Button button(int resource) {
        String text = activity.getString(resource);
        for (Button candidate : views(Button.class)) {
            if (text.contentEquals(candidate.getText())) return candidate;
        }
        throw new AssertionError("No button: " + text);
    }

    private boolean hasText(int resource) { return hasLiteralText(activity.getString(resource)); }

    private boolean hasLiteralText(String text) {
        for (TextView candidate : views(TextView.class)) {
            if (text.contentEquals(candidate.getText())) return true;
        }
        return false;
    }

    private <T extends View> List<T> views(Class<T> type) {
        List<T> result = new ArrayList<>();
        collect(activity.findViewById(android.R.id.content), type, result);
        return result;
    }

    private static <T extends View> void collect(View view, Class<T> type, List<T> result) {
        if (type.isInstance(view)) result.add(type.cast(view));
        if (view instanceof ViewGroup group) {
            for (int index = 0; index < group.getChildCount(); index++) {
                collect(group.getChildAt(index), type, result);
            }
        }
    }

    private static int[] expectedLabels(NotificationType type) {
        return switch (type) {
            case FOCUS -> new int[]{R.string.filter_focus_title, R.string.filter_focus_description};
            case ISLAND_CONTENT -> new int[]{R.string.filter_island_title, R.string.filter_island_description};
            case UPDATABLE_FOCUS -> new int[]{R.string.filter_updatable_title, R.string.filter_updatable_description};
            case PROMOTED_ONGOING -> new int[]{R.string.filter_promoted_title, R.string.filter_promoted_description};
            case REQUEST_PROMOTION -> new int[]{R.string.filter_request_title, R.string.filter_request_description};
            case PERSISTENT -> new int[]{R.string.filter_persistent_title, R.string.filter_persistent_description};
            case ONGOING_EVENT -> new int[]{R.string.filter_ongoing_title, R.string.filter_ongoing_description};
            case NO_CLEAR -> new int[]{R.string.filter_no_clear_title, R.string.filter_no_clear_description};
            case FOREGROUND_SERVICE -> new int[]{R.string.filter_foreground_title, R.string.filter_foreground_description};
            case NOT_CLEARABLE -> new int[]{R.string.filter_not_clearable_title, R.string.filter_not_clearable_description};
            case HEADS_UP_PINNED -> new int[]{R.string.filter_pinned_title, R.string.filter_pinned_description};
            case MEDIA -> new int[]{R.string.filter_media_title, R.string.filter_media_description};
            case CALL -> new int[]{R.string.filter_call_title, R.string.filter_call_description};
            case SILENT -> new int[]{R.string.filter_silent_title, R.string.filter_silent_description};
            case FOLDED -> new int[]{R.string.filter_folded_title, R.string.filter_folded_description};
        };
    }
}
