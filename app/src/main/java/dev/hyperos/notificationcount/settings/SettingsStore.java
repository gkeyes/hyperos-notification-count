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
        public final boolean temporaryColor;
        public final int colorDurationSeconds;
        public final int digitColor;
        public final int badgeContrast;
        public final int digitWeight;
        public final Status status;

        State(int mask, Status status) {
            this(Values.DEFAULT.withMask(mask), status);
        }

        State(Values values, Status status) {
            this.mask = values.mask;
            this.iconColorEnabled = values.color;
            this.temporaryColor = values.temporary;
            this.colorDurationSeconds = values.duration;
            this.digitColor = values.digitColor;
            this.badgeContrast = values.contrast;
            this.digitWeight = values.weight;
            this.status = status;
        }

        Values values() {
            return new Values(mask, iconColorEnabled, temporaryColor, colorDurationSeconds,
                    digitColor, badgeContrast, digitWeight);
        }
    }

    /** One atomic group of saved settings. */
    static final class Values {
        static final Values DEFAULT = new Values(0, false, false,
                FilterPreferences.DEFAULT_COLOR_DURATION, FilterPreferences.DIGIT_COLOR_AUTO,
                FilterPreferences.DEFAULT_BADGE_CONTRAST, FilterPreferences.WEIGHT_NORMAL);

        final int mask;
        final boolean color;
        final boolean temporary;
        final int duration;
        final int digitColor;
        final int contrast;
        final int weight;

        Values(int mask, boolean color, boolean temporary, int duration,
                int digitColor, int contrast, int weight) {
            this.mask = mask & FilterPreferences.KNOWN_MASK;
            this.color = color;
            this.temporary = temporary;
            this.duration = FilterPreferences.normalizeColorDuration(duration);
            this.digitColor = FilterPreferences.normalizeDigitColor(digitColor);
            this.contrast = FilterPreferences.normalizeBadgeContrast(contrast);
            this.weight = FilterPreferences.normalizeDigitWeight(weight);
        }

        static Values read(SharedPreferences preferences) {
            return new Values(FilterPreferences.read(preferences),
                    FilterPreferences.readIconColor(preferences),
                    FilterPreferences.readTemporaryColor(preferences),
                    FilterPreferences.readColorDuration(preferences),
                    FilterPreferences.readDigitColor(preferences),
                    FilterPreferences.readBadgeContrast(preferences),
                    FilterPreferences.readDigitWeight(preferences));
        }

        boolean write(SharedPreferences target) {
            return target.edit().putInt(FilterPreferences.EXCLUDED_MASK, mask)
                    .putBoolean(FilterPreferences.ICON_COLOR_ENABLED, color)
                    .putBoolean(FilterPreferences.ICON_COLOR_TEMPORARY, temporary)
                    .putInt(FilterPreferences.ICON_COLOR_DURATION, duration)
                    .putInt(FilterPreferences.DIGIT_COLOR, digitColor)
                    .putInt(FilterPreferences.BADGE_CONTRAST, contrast)
                    .putInt(FilterPreferences.DIGIT_WEIGHT, weight).commit();
        }

        Values withMask(int value) {
            return new Values(value, color, temporary, duration, digitColor, contrast, weight);
        }

        boolean sameAs(Values other) {
            return mask == other.mask && color == other.color && temporary == other.temporary
                    && duration == other.duration && digitColor == other.digitColor
                    && contrast == other.contrast && weight == other.weight;
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
        publish(state.values(), Status.UNAVAILABLE);
    }

    public void retry() {
        if (source == null) {
            publish(state.values(), Status.WAITING);
        } else if (state.status != Status.CONNECTING && state.status != Status.SAVING) {
            load();
        }
    }

    private void load() {
        long token = ++generation;
        Supplier<SharedPreferences> currentSource = source;
        Values confirmed = state.values();
        boolean restore = needsRestore;
        preferences = null;
        publish(confirmed, Status.CONNECTING);
        worker.execute(() -> {
            try {
                SharedPreferences loaded = currentSource.get();
                // libxposed's app-side Editor updates its cache before the IPC completes.
                // After a failed save, re-confirm the last acknowledged values before enabling UI.
                if (restore && !confirmed.write(loaded)) {
                    throw new IllegalStateException("Remote preferences unavailable");
                }
                Values values = Values.read(loaded);
                main.execute(() -> {
                    if (token != generation) return;
                    preferences = loaded;
                    needsRestore = false;
                    publish(values, Status.READY);
                });
            } catch (RuntimeException error) {
                main.execute(() -> {
                    if (token == generation) publish(confirmed, Status.UNAVAILABLE);
                });
            }
        });
    }

    public void setExcluded(NotificationType type, boolean excluded) {
        setMask(excluded ? state.mask | type.bit : state.mask & ~type.bit);
    }

    public void reset() { setMask(0); }

    public void setIconColorEnabled(boolean enabled) {
        Values v = state.values();
        setValues(new Values(v.mask, enabled, v.temporary, v.duration, v.digitColor, v.contrast, v.weight));
    }

    public void setTemporaryColor(boolean temporary) {
        Values v = state.values();
        setValues(new Values(v.mask, v.color, temporary, v.duration, v.digitColor, v.contrast, v.weight));
    }

    public void setColorDurationSeconds(int seconds) {
        Values v = state.values();
        setValues(new Values(v.mask, v.color, v.temporary, seconds, v.digitColor, v.contrast, v.weight));
    }

    public void setDigitColor(int digitColor) {
        Values v = state.values();
        setValues(new Values(v.mask, v.color, v.temporary, v.duration, digitColor, v.contrast, v.weight));
    }

    public void setBadgeContrast(int tenths) {
        Values v = state.values();
        setValues(new Values(v.mask, v.color, v.temporary, v.duration, v.digitColor, tenths, v.weight));
    }

    public void setDigitWeight(int weight) {
        Values v = state.values();
        setValues(new Values(v.mask, v.color, v.temporary, v.duration, v.digitColor, v.contrast, weight));
    }

    private void setMask(int mask) {
        setValues(state.values().withMask(mask));
    }

    private void setValues(Values next) {
        if (state.status != Status.READY || preferences == null) return;
        Values previous = state.values();
        if (previous.sameAs(next)) return;
        long token = generation;
        SharedPreferences target = preferences;
        publish(next, Status.SAVING);
        worker.execute(() -> {
            boolean saved;
            try {
                saved = next.write(target);
            } catch (RuntimeException error) {
                saved = false;
            }
            if (!saved) {
                // Restore the optimistic library cache as well as attempting a remote rollback.
                try { previous.write(target); }
                catch (RuntimeException ignored) { }
            }
            boolean acknowledged = saved;
            main.execute(() -> {
                if (token != generation) return;
                if (acknowledged) {
                    publish(next, Status.READY);
                } else {
                    preferences = null;
                    needsRestore = true;
                    publish(previous, Status.SAVE_FAILED);
                }
            });
        });
    }

    private void publish(Values values, Status status) {
        state = new State(values, status);
        for (Consumer<State> observer : new LinkedHashSet<>(observers)) observer.accept(state);
    }
}
