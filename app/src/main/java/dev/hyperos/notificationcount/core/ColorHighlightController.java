package dev.hyperos.notificationcount.core;

import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** Main-thread, one-shot highlight window. Notification refreshes cannot extend an expired pulse. */
public final class ColorHighlightController {
    public interface Scheduler {
        void schedule(Runnable task, long delayMillis);
        void cancel(Runnable task);
    }

    private final LongSupplier clock;
    private final Scheduler scheduler;
    private final Consumer<AppIconSource> selection;
    private final Runnable timeout = this::onTimeout;
    private AppIconSource source;
    private boolean enabled;
    private boolean temporary;
    private int seconds = 5;
    private long newestArrival;
    private long deadline;

    public ColorHighlightController(LongSupplier clock, Scheduler scheduler,
            Consumer<AppIconSource> selection) {
        this.clock = clock;
        this.scheduler = scheduler;
        this.selection = selection;
    }

    public void update(AppIconSource source, long arrival, boolean enabled,
            boolean temporary, int seconds) {
        boolean reconfigured = !this.enabled || this.temporary != temporary || this.seconds != seconds;
        this.enabled = enabled;
        this.temporary = temporary;
        this.seconds = seconds;
        this.source = source;
        if (!enabled) {
            newestArrival = 0;
            stop();
        } else if (source == null) {
            stop();
        } else {
            boolean added = arrival > newestArrival;
            newestArrival = Math.max(newestArrival, arrival);
            if (!temporary) stop();
            else if (reconfigured || added) {
                scheduler.cancel(timeout);
                deadline = clock.getAsLong() + seconds * 1000L;
                scheduler.schedule(timeout, seconds * 1000L);
            }
        }
        selection.accept(activeSource());
    }

    public AppIconSource activeSource() {
        if (!enabled || source == null) return null;
        return !temporary || deadline > clock.getAsLong() ? source : null;
    }

    /** Also called before drawing: elapsed real time continues while Handler's uptime sleeps. */
    public void expireIfNeeded() {
        if (deadline != 0 && clock.getAsLong() >= deadline) {
            stop();
            selection.accept(null);
        }
    }

    private void onTimeout() {
        if (deadline == 0) return;
        long remaining = deadline - clock.getAsLong();
        if (remaining > 0) scheduler.schedule(timeout, remaining);
        else expireIfNeeded();
    }

    private void stop() {
        if (deadline != 0) scheduler.cancel(timeout);
        deadline = 0;
    }
}
