package dev.hyperos.notificationcount.settings;

import android.content.SharedPreferences;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;

import dev.hyperos.notificationcount.core.NotificationType;

/** UI state stays on the main executor; framework reads/writes use a serial worker. */
public final class SettingsStore {
    public enum Status { WAITING, CONNECTING, READY, SAVING, UNAVAILABLE, SAVE_FAILED }

    public static final class State {
        public final int mask;
        public final boolean iconColorEnabled;
        public final Status status;

        State(int mask, Status status) {
            this(mask, false, status);
        }

        State(int mask, boolean iconColorEnabled, Status status) {
            this.mask = mask;
            this.iconColorEnabled = iconColorEnabled;
            this.status = status;
        }
    }

    private final Executor worker;
    private final Executor main;
    private final Set<Consumer<State>> observers = new LinkedHashSet<>();
    private State state = new State(0, Status.WAITING);
    private Object connection;
    private Supplier<SharedPreferences> source;
    private SharedPreferences preferences;
    private long generation;
    private boolean needsRestore;

    public SettingsStore(Executor worker, Executor main) {
        this.worker = worker;
        this.main = main;
    }

    public State state() { return state; }

    public void observe(Consumer<State> observer) {
        observers.add(observer);
        observer.accept(state);
    }

    public void removeObserver(Consumer<State> observer) { observers.remove(observer); }

    public void connect(Object identity, Supplier<SharedPreferences> remotePreferences) {
        boolean sameService = connection == identity;
        if (sameService && (state.status == Status.READY || state.status == Status.SAVING
                || state.status == Status.CONNECTING)) return;
        connection = identity;
        source = remotePreferences;
        // A new service has a fresh remote cache. Do not overwrite its stored configuration.
        if (!sameService) needsRestore = false;
        load();
    }

    public void disconnect(Object identity) {
        if (connection != identity) return;
        generation++;
        preferences = null;
        connection = null;
        source = null;
        publish(state.mask, Status.UNAVAILABLE);
    }

    public void retry() {
        if (source == null) {
            publish(state.mask, Status.WAITING);
        } else if (state.status != Status.CONNECTING && state.status != Status.SAVING) {
            load();
        }
    }

    private void load() {
        long token = ++generation;
        Supplier<SharedPreferences> currentSource = source;
        int confirmedMask = state.mask;
        boolean confirmedColor = state.iconColorEnabled;
        boolean restore = needsRestore;
        preferences = null;
        publish(confirmedMask, Status.CONNECTING);
        worker.execute(() -> {
            try {
                SharedPreferences loaded = currentSource.get();
                // libxposed's app-side Editor updates its cache before the IPC completes.
                // After a failed save, re-confirm the last acknowledged mask before enabling UI.
                if (restore && !write(loaded, confirmedMask, confirmedColor)) {
                    throw new IllegalStateException("Remote preferences unavailable");
                }
                int mask = FilterPreferences.read(loaded);
                boolean color = FilterPreferences.readIconColor(loaded);
                main.execute(() -> {
                    if (token != generation) return;
                    preferences = loaded;
                    needsRestore = false;
                    publish(mask, color, Status.READY);
                });
            } catch (RuntimeException error) {
                main.execute(() -> {
                    if (token == generation) publish(confirmedMask, confirmedColor, Status.UNAVAILABLE);
                });
            }
        });
    }

    public void setExcluded(NotificationType type, boolean excluded) {
        setMask(excluded ? state.mask | type.bit : state.mask & ~type.bit);
    }

    public void reset() { setMask(0); }

    public void setIconColorEnabled(boolean enabled) { setValues(state.mask, enabled); }

    private void setMask(int mask) {
        setValues(mask, state.iconColorEnabled);
    }

    private void setValues(int mask, boolean color) {
        if (state.status != Status.READY || preferences == null) return;
        int nextMask = mask & FilterPreferences.KNOWN_MASK;
        int previousMask = state.mask;
        boolean previousColor = state.iconColorEnabled;
        if (previousMask == nextMask && previousColor == color) return;
        long token = generation;
        SharedPreferences target = preferences;
        publish(nextMask, color, Status.SAVING);
        worker.execute(() -> {
            boolean saved;
            try {
                saved = write(target, nextMask, color);
            } catch (RuntimeException error) {
                saved = false;
            }
            if (!saved) {
                // Restore the optimistic library cache as well as attempting a remote rollback.
                try { write(target, previousMask, previousColor); }
                catch (RuntimeException ignored) { }
            }
            boolean acknowledged = saved;
            main.execute(() -> {
                if (token != generation) return;
                if (acknowledged) {
                    publish(nextMask, color, Status.READY);
                } else {
                    preferences = null;
                    needsRestore = true;
                    publish(previousMask, previousColor, Status.SAVE_FAILED);
                }
            });
        });
    }

    private void publish(int mask, Status status) {
        publish(mask, state.iconColorEnabled, status);
    }

    private static boolean write(SharedPreferences target, int mask, boolean color) {
        return target.edit().putInt(FilterPreferences.EXCLUDED_MASK, mask)
                .putBoolean(FilterPreferences.ICON_COLOR_ENABLED, color).commit();
    }

    private void publish(int mask, boolean color, Status status) {
        state = new State(mask, color, status);
        for (Consumer<State> observer : new LinkedHashSet<>(observers)) observer.accept(state);
    }
}
