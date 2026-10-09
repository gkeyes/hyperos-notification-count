package dev.hyperos.notificationcount.core;

import static org.junit.Assert.*;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Executor;
import org.junit.Test;

public class IconColorCoordinatorTest {
    private final Queue<Runnable> tasks = new ArrayDeque<>();
    private final List<AppIconSource> loaded = new ArrayList<>();
    private final List<Integer> displayed = new ArrayList<>();
    private long now;
    private boolean fail;
    private final Executor worker = tasks::add;
    private final IconColorCoordinator coordinator = new IconColorCoordinator(source -> {
        loaded.add(source);
        if (fail) throw new IllegalStateException("Unreadable resources");
        return 0xff000000 | source.userId;
    }, worker, Runnable::run, () -> now, displayed::add);

    @Test public void offDoesNoWorkAndRepeatedEventsUseTheCache() {
        coordinator.select(null);
        assertTrue(tasks.isEmpty());
        AppIconSource a = source("a", 0);
        coordinator.select(a);
        coordinator.select(a);
        assertEquals(1, tasks.size());
        finish();
        coordinator.select(a);
        assertTrue(tasks.isEmpty());
        assertEquals(Arrays.asList(a), loaded);
        assertEquals(Arrays.asList(0xff000000), displayed);
    }

    @Test public void aResultAfterDisablingCannotRestoreColor() {
        coordinator.select(source("a", 0));
        coordinator.select(null);
        finish();
        assertTrue(displayed.isEmpty());
        assertTrue(tasks.isEmpty());
    }

    @Test public void rapidSourcesAreCoalescedAndStaleColorsNeverFlash() {
        AppIconSource a = source("a", 0);
        AppIconSource b = source("b", 1);
        AppIconSource c = source("c", 2);
        coordinator.select(a);
        coordinator.select(b);
        coordinator.select(c);
        assertEquals(1, tasks.size());
        finish();
        assertTrue(displayed.isEmpty());
        finish();
        assertEquals(Arrays.asList(a, c), loaded);
        assertEquals(Arrays.asList(0xff000002), displayed);
    }

    @Test public void appClonesNeverShareAUserColorCache() {
        coordinator.select(source("same", 0));
        finish();
        coordinator.select(source("same", 999));
        assertNull(displayed.get(displayed.size()-1));
        finish();
        assertEquals(2, loaded.size());
        assertEquals(Integer.valueOf(0xff0003e7), displayed.get(displayed.size()-1));
    }

    @Test public void negativeCachePreventsRetryStormsAndRecoversOnALaterEvent() {
        AppIconSource a = source("a", 0);
        fail = true;
        coordinator.select(a);
        finish();
        coordinator.select(a);
        assertTrue(tasks.isEmpty());
        assertTrue(displayed.isEmpty());
        now = 60_000;
        fail = false;
        assertTrue(tasks.isEmpty()); // No timers or polling.
        coordinator.select(a);
        finish();
        assertEquals(2, loaded.size());
        assertEquals(Integer.valueOf(0xff000000), displayed.get(0));
    }

    @Test public void expiredPositiveColorsReloadOnlyOnAnEvent() {
        AppIconSource a = source("a", 0);
        coordinator.select(a);
        finish();
        now = 15 * 60_000;
        assertTrue(tasks.isEmpty());
        coordinator.select(a);
        finish();
        assertEquals(2, loaded.size());
    }

    @Test public void cacheRetainsOnlySixtyFourSources() {
        for (int i = 0; i < 65; i++) {
            coordinator.select(source("app" + i, 0));
            finish();
        }
        coordinator.select(source("app1", 0));
        assertTrue(tasks.isEmpty());
        coordinator.select(source("app0", 0));
        assertEquals(1, tasks.size());
        finish();
        assertEquals(66, loaded.size());
    }

    private AppIconSource source(String name, int user) { return new AppIconSource(name, user); }
    private void finish() {
        Runnable task = tasks.poll();
        assertNotNull("Expected a background resource load", task);
        task.run();
    }
}
