package dev.hyperos.notificationcount.core;

import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** Main-thread state, one background load at a time, and bounded positive/negative caches. */
public final class IconColorCoordinator {
    @FunctionalInterface public interface Loader { Integer load(AppIconSource source) throws Exception; }
    private static final int CAPACITY = 64;
    private static final long COLOR_TTL_MS = 15 * 60_000L;
    private static final long FALLBACK_TTL_MS = 60_000L;
    private final LinkedHashMap<AppIconSource, Entry> cache = new LinkedHashMap<>(16, .75f, true);
    private final Loader loader;
    private final Executor worker;
    private final Executor main;
    private final LongSupplier clock;
    private final Consumer<Integer> changed;
    private AppIconSource selected;
    private Integer displayed;
    private boolean loading;

    public IconColorCoordinator(Loader loader, Executor worker, Executor main,
            LongSupplier clock, Consumer<Integer> changed) {
        this.loader = loader;
        this.worker = worker;
        this.main = main;
        this.clock = clock;
        this.changed = changed;
    }

    /** null disables coloring, including while an older resource load is still completing. */
    public void select(AppIconSource source) {
        selected = source;
        if (source == null) {
            display(null);
            return;
        }
        Entry saved = cache.get(source);
        if (saved != null && clock.getAsLong() - saved.time < saved.ttl()) {
            display(saved.color);
            return;
        }
        display(null);
        if (!loading) start(source);
    }

    private void start(AppIconSource source) {
        loading = true;
        worker.execute(() -> {
            Integer color;
            try { color = loader.load(source); }
            // A broken or inaccessible app drawable must never disable counting or crash SystemUI.
            catch (Throwable failure) { color = null; }
            Integer result = color;
            main.execute(() -> {
                loading = false;
                cache.put(source, new Entry(result, clock.getAsLong()));
                while (cache.size() > CAPACITY) cache.remove(cache.keySet().iterator().next());
                // Coalesce A -> B -> C into A -> C, and reject results after disabling/changing user.
                select(selected);
            });
        });
    }

    private void display(Integer color) {
        if (Objects.equals(displayed, color)) return;
        displayed = color;
        changed.accept(color);
    }

    private static final class Entry {
        final Integer color;
        final long time;
        Entry(Integer color, long time) { this.color = color; this.time = time; }
        long ttl() { return color == null ? FALLBACK_TTL_MS : COLOR_TTL_MS; }
    }
}
