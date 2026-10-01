package dev.hyperos.notificationcount.settings;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Build;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import dev.hyperos.notificationcount.R;
import dev.hyperos.notificationcount.core.NotificationType;
import dev.hyperos.notificationcount.render.CountDrawable;
import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Tests the signed shrunk APK using Android accessibility, without Compose test-only APIs. */
@RunWith(AndroidJUnit4.class)
public class ReleaseSmokeTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private Activity activity;
    private ModuleApplication application;
    private SharedPreferences preferences;

    @Before public void connectTestPreferences() {
        assertEquals(37, Build.VERSION.SDK_INT);
        onMain(() -> {
            application = (ModuleApplication) instrumentation.getTargetContext().getApplicationContext();
            preferences = application.getSharedPreferences("release-smoke", Context.MODE_PRIVATE);
            assertTrue(preferences.edit().clear().commit());
            application.getSettingsStore().disconnect(application);
            application.getSettingsStore().connect(application, () -> preferences);
            return null;
        });
        awaitReady(0);
        activity = instrumentation.startActivitySync(new Intent(application, SettingsActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        waitUntil("Settings page did not open", () -> findNode(node ->
                application.getString(R.string.settings_title).contentEquals(text(node))) != null);
    }

    @After public void closeSettings() {
        if (activity != null) onMain(() -> { activity.finish(); return null; });
        instrumentation.waitForIdleSync();
    }

    @Test public void allFiltersPersistReopenAndResetInTheShrunkActivity() {
        assertEquals(15, NotificationType.values().length);
        int mask = 0;
        for (NotificationType type : NotificationType.values()) {
            click(assertFilter(type, false, true));
            mask |= type.bit;
            awaitReady(mask);
            assertFilter(type, true, true);
            assertEquals(mask, preferences.getInt(FilterPreferences.EXCLUDED_MASK, -1));
        }
        assertEquals(FilterPreferences.KNOWN_MASK, mask);
        Instrumentation.ActivityMonitor monitor = instrumentation.addMonitor(
                SettingsActivity.class.getName(), null, false);
        try {
            onMain(() -> { activity.recreate(); return null; });
            Activity reopened = instrumentation.waitForMonitorWithTimeout(monitor, 10_000);
            assertNotNull("Settings activity did not recreate", reopened);
            activity = reopened;
        } finally {
            instrumentation.removeMonitor(monitor);
        }
        scrollToTop();
        for (NotificationType type : NotificationType.values()) assertFilter(type, true, true);
        click(findScrolling(node -> application.getString(R.string.settings_reset).contentEquals(text(node))));
        awaitReady(0);
        assertEquals(0, preferences.getInt(FilterPreferences.EXCLUDED_MASK, -1));
        scrollToTop();
        for (NotificationType type : NotificationType.values()) assertFilter(type, false, true);
    }

    @Test public void connectionLossDisablesFiltersAndManagerHasNoLauncher() {
        onMain(() -> { application.getSettingsStore().disconnect(application); return null; });
        for (NotificationType type : NotificationType.values()) assertFilter(type, false, false);
        click(findScrolling(node -> application.getString(R.string.settings_retry).contentEquals(text(node))));
        scrollToTop();
        for (NotificationType type : NotificationType.values()) assertFilter(type, false, false);
        onMain(() -> { application.getSettingsStore().connect(application, () -> preferences); return null; });
        awaitReady(0);
        scrollToTop();
        for (NotificationType type : NotificationType.values()) assertFilter(type, false, true);
        Intent manager = new Intent(Intent.ACTION_MAIN)
                .addCategory("de.robv.android.xposed.category.MODULE_SETTINGS").setPackage(application.getPackageName());
        Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setPackage(application.getPackageName());
        assertEquals(1, application.getPackageManager().queryIntentActivities(manager, 0).size());
        assertTrue(application.getPackageManager().queryIntentActivities(launcher, 0).isEmpty());
    }

    @Test public void everyCountVectorSurvivesShrinkingAndDrawsInBothTints() {
        CountDrawable drawable = new CountDrawable(application.getResources());
        assertTrue(drawable.getFailureReason(), drawable.isHealthy());
        int size = drawable.getIntrinsicWidth();
        drawable.setBounds(0, 0, size, size);
        for (int pixel : pixels(drawable, 0, size)) assertEquals(0, Color.alpha(pixel));
        for (int tint : new int[]{Color.WHITE, Color.BLACK}) {
            drawable.setTint(tint);
            for (int count = 1; count <= 10; count++) {
                int painted = 0;
                for (int pixel : pixels(drawable, count, size)) if (Color.alpha(pixel) > 0) {
                    painted++;
                    assertEquals("Wrong tint for count " + count, tint & 0xffffff, pixel & 0xffffff);
                }
                assertTrue("No pixels for count " + count, painted > 0);
            }
            assertArrayEquals(pixels(drawable, 10, size), pixels(drawable, 99, size));
        }
    }

    private int[] pixels(CountDrawable drawable, int count, int size) {
        drawable.setCount(count);
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        drawable.draw(new Canvas(bitmap));
        int[] pixels = new int[size * size];
        bitmap.getPixels(pixels, 0, size, 0, 0, size, size);
        bitmap.recycle();
        assertTrue(drawable.getFailureReason(), drawable.isHealthy());
        return pixels;
    }

    private <T> T onMain(Supplier<T> action) {
        AtomicReference<T> result = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> result.set(action.get()));
        return result.get();
    }

    private void awaitReady(int mask) {
        waitUntil("Settings mask " + mask + " was not saved", () -> onMain(() -> {
            SettingsStore.State state = application.getSettingsStore().state();
            return state.status == SettingsStore.Status.READY && state.mask == mask;
        }));
    }

    private void waitUntil(String message, BooleanSupplier condition) {
        long deadline = SystemClock.uptimeMillis() + 10_000;
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync();
            if (condition.getAsBoolean()) return;
            SystemClock.sleep(50);
        }
        throw new AssertionError(message);
    }

    private AccessibilityNodeInfo findNode(Predicate<AccessibilityNodeInfo> predicate) {
        AccessibilityNodeInfo root = instrumentation.getUiAutomation().getRootInActiveWindow();
        if (root == null) return null;
        ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            AccessibilityNodeInfo node = queue.removeFirst();
            if (node.isVisibleToUser() && predicate.test(node)) return node;
            for (int index = 0; index < node.getChildCount(); index++) {
                AccessibilityNodeInfo child = node.getChild(index);
                if (child != null) queue.add(child);
            }
        }
        return null;
    }

    private static boolean hasAction(AccessibilityNodeInfo node, int action) {
        return node.getActionList().stream().anyMatch(candidate -> candidate.getId() == action);
    }

    private boolean scroll(int action) {
        AccessibilityNodeInfo node = findNode(candidate -> candidate.isScrollable() && hasAction(candidate, action));
        if (node == null || !node.performAction(action)) return false;
        SystemClock.sleep(250);
        return true;
    }

    private void scrollToTop() {
        // Recreate may return before the new window exposes its restored scroll state.
        waitUntil("Settings scroll view is not ready", () ->
                findNode(AccessibilityNodeInfo::isScrollable) != null);
        for (int attempt = 0; attempt < 15; attempt++)
            if (!scroll(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) return;
        throw new AssertionError("Settings page did not scroll to the top");
    }

    private AccessibilityNodeInfo findScrolling(Predicate<AccessibilityNodeInfo> predicate) {
        for (int attempt = 0; attempt < 15; attempt++) {
            AccessibilityNodeInfo node = findNode(predicate);
            if (node != null) return node;
            if (!scroll(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) break;
        }
        throw new AssertionError("Settings control could not be reached");
    }

    private void click(AccessibilityNodeInfo node) {
        for (int attempt = 0; attempt < 6 && node != null; attempt++, node = node.getParent()) {
            if (hasAction(node, AccessibilityNodeInfo.ACTION_CLICK)) {
                assertTrue("Settings control is disabled", node.isEnabled());
                assertTrue("Settings control did not click", node.performAction(AccessibilityNodeInfo.ACTION_CLICK));
                return;
            }
        }
        throw new AssertionError("Settings control has no clickable parent");
    }

    private AccessibilityNodeInfo assertFilter(NotificationType type, boolean checked, boolean enabled) {
        int[] labels = LABELS[type.ordinal()];
        String description = application.getString(labels[0]) + "。" + application.getString(labels[1]);
        Predicate<AccessibilityNodeInfo> matches = node -> description.contentEquals(
                node.getContentDescription() == null ? "" : node.getContentDescription());
        findScrolling(matches);
        waitUntil("Wrong state for " + type.key, () -> {
            AccessibilityNodeInfo node = findNode(matches);
            return node != null && node.isCheckable() && node.isChecked() == checked && node.isEnabled() == enabled;
        });
        return findNode(matches);
    }

    private static CharSequence text(AccessibilityNodeInfo node) {
        return node.getText() == null ? "" : node.getText();
    }

    private static final int[][] LABELS = {
        {R.string.filter_focus_title, R.string.filter_focus_description},
        {R.string.filter_island_title, R.string.filter_island_description},
        {R.string.filter_updatable_title, R.string.filter_updatable_description},
        {R.string.filter_promoted_title, R.string.filter_promoted_description},
        {R.string.filter_request_title, R.string.filter_request_description},
        {R.string.filter_persistent_title, R.string.filter_persistent_description},
        {R.string.filter_ongoing_title, R.string.filter_ongoing_description},
        {R.string.filter_no_clear_title, R.string.filter_no_clear_description},
        {R.string.filter_foreground_title, R.string.filter_foreground_description},
        {R.string.filter_not_clearable_title, R.string.filter_not_clearable_description},
        {R.string.filter_pinned_title, R.string.filter_pinned_description},
        {R.string.filter_media_title, R.string.filter_media_description},
        {R.string.filter_call_title, R.string.filter_call_description},
        {R.string.filter_silent_title, R.string.filter_silent_description},
        {R.string.filter_folded_title, R.string.filter_folded_description},
    };
}
