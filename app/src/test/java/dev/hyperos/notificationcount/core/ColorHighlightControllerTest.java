package dev.hyperos.notificationcount.core;

import static org.junit.Assert.*;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import org.junit.Test;

public class ColorHighlightControllerTest {
    private long now;
    private final FakeScheduler scheduler = new FakeScheduler();
    private final List<AppIconSource> selected = new ArrayList<>();
    private final AppIconSource app = new AppIconSource("test.app", 0);
    private final ColorHighlightController highlight = new ColorHighlightController(
            () -> now, scheduler, selected::add);

    @Test public void persistentModeHasNoTimerAndNeverExpires() {
        highlight.update(app, 1, true, false, 5);
        assertNull(scheduler.pending);
        now = 100_000;
        highlight.expireIfNeeded();
        assertEquals(app, highlight.activeSource());
    }

    @Test public void eachOfferedDurationExpiresExactlyOnceAndDoesNotRestartOnRefresh() {
        for (int seconds : new int[]{1, 3, 5, 10, 15}) {
            highlight.update(app, seconds, false, true, seconds);
            highlight.update(app, seconds, true, true, seconds);
            assertEquals(seconds * 1000L, scheduler.delay);
            now += seconds * 1000L - 1;
            assertEquals(app, highlight.activeSource());
            now++;
            scheduler.fire();
            assertNull(highlight.activeSource());
            assertNull(scheduler.pending);
            highlight.update(app, seconds, true, true, seconds);
            assertNull(highlight.activeSource());
            assertNull(scheduler.pending);
        }
    }

    @Test public void progressRefreshesDoNotExtendTheOriginalDeadline() {
        highlight.update(app, 1, true, true, 5);
        now = 4000;
        highlight.update(app, 1, true, true, 5);
        assertEquals(1, scheduler.scheduled);
        now = 5000;
        scheduler.fire();
        assertNull(highlight.activeSource());
    }

    @Test public void aNewNotificationFromTheSameAppRestartsTheWindow() {
        highlight.update(app, 1, true, true, 5);
        now = 4000;
        highlight.update(app, 2, true, true, 5);
        now = 5000;
        assertEquals(app, highlight.activeSource());
        now = 9000;
        scheduler.fire();
        assertNull(highlight.activeSource());
        assertEquals(2, scheduler.scheduled);
    }

    @Test public void removingTheLatestNotificationDoesNotStartAnotherWindow() {
        highlight.update(app, 2, true, true, 5);
        now = 4000;
        AppIconSource older = new AppIconSource("older.app", 0);
        highlight.update(older, 1, true, true, 5);
        assertEquals(older, highlight.activeSource());
        assertEquals(1, scheduler.scheduled);
        now = 5000;
        scheduler.fire();
        highlight.update(older, 1, true, true, 5);
        assertNull(highlight.activeSource());
    }

    @Test public void disablingTheMasterSwitchCancelsAndReenablingStartsAFreshWindow() {
        highlight.update(app, 1, true, true, 5);
        highlight.update(app, 1, false, true, 5);
        assertNull(scheduler.pending);
        assertNull(highlight.activeSource());
        now = 9000;
        highlight.update(app, 1, true, true, 5);
        assertEquals(app, highlight.activeSource());
        assertEquals(5000, scheduler.delay);
    }

    @Test public void disablingTemporaryModeRestoresPersistentColorAndCancelsTheTimer() {
        highlight.update(app, 1, true, true, 5);
        now = 5000;
        scheduler.fire();
        highlight.update(app, 1, true, false, 5);
        assertEquals(app, highlight.activeSource());
        assertNull(scheduler.pending);
    }

    @Test public void changingDurationStartsTheSelectedDurationFromNow() {
        highlight.update(app, 1, true, true, 5);
        now = 4000;
        highlight.update(app, 1, true, true, 1);
        assertEquals(1000, scheduler.delay);
        now = 5000;
        scheduler.fire();
        assertNull(highlight.activeSource());
    }

    @Test public void clearingAllCountedNotificationsCancelsAndUnfilteringOldOnesDoesNotPulse() {
        highlight.update(app, 1, true, true, 5);
        highlight.update(null, 0, true, true, 5);
        assertNull(scheduler.pending);
        highlight.update(app, 1, true, true, 5);
        assertNull(highlight.activeSource());
    }

    @Test public void elapsedTimeWhileAsleepIsCheckedBeforeDrawingWithoutWaitingForHandler() {
        highlight.update(app, 1, true, true, 1);
        now = 60_000;
        assertNull(highlight.activeSource());
        highlight.expireIfNeeded();
        assertNull(scheduler.pending);
        assertNull(selected.get(selected.size()-1));
    }

    @Test public void anEarlyCallbackDoesNotExpireTheColorAndReschedulesOnlyTheRemainder() {
        highlight.update(app, 1, true, true, 5);
        now = 3000;
        scheduler.fire();
        assertEquals(app, highlight.activeSource());
        assertEquals(2000, scheduler.delay);
        now = 5000;
        scheduler.fire();
        assertNull(highlight.activeSource());
    }

    @Test public void aLateBackgroundColorCannotRestoreAnExpiredHighlight() {
        Queue<Runnable> tasks = new ArrayDeque<>();
        List<Integer> displayed = new ArrayList<>();
        IconColorCoordinator colors = new IconColorCoordinator(source -> 0xff03d769,
                tasks::add, Runnable::run, () -> now, displayed::add);
        ColorHighlightController window = new ColorHighlightController(
                () -> now, scheduler, colors::select);
        window.update(app, 1, true, true, 1);
        now = 1000;
        scheduler.fire();
        assertNull(window.activeSource());
        tasks.remove().run();
        assertTrue(displayed.isEmpty());
        assertTrue(tasks.isEmpty());
    }

    private static final class FakeScheduler implements ColorHighlightController.Scheduler {
        Runnable pending;
        long delay;
        int scheduled;

        @Override public void schedule(Runnable task, long delayMillis) {
            assertNull("At most one expiry callback may be pending", pending);
            pending = task;
            delay = delayMillis;
            scheduled++;
        }
        @Override public void cancel(Runnable task) {
            if (pending == task) pending = null;
        }
        void fire() {
            Runnable task = pending;
            assertNotNull(task);
            pending = null;
            task.run();
        }
    }
}
