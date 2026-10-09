package dev.hyperos.notificationcount.hook;

import android.content.res.Resources;
import android.content.Context;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.concurrent.Executors;

import dev.hyperos.notificationcount.NotificationCountModule;
import dev.hyperos.notificationcount.core.AppIconSource;
import dev.hyperos.notificationcount.core.ColorHighlightController;
import dev.hyperos.notificationcount.core.IconColorCoordinator;
import dev.hyperos.notificationcount.core.ReadableIconColor;
import dev.hyperos.notificationcount.render.AppIconColorLoader;
import dev.hyperos.notificationcount.render.CountDrawable;

/** Retains native children and bindings; the number lives in the top-bar container's overlay. */
final class StatusBarRenderer {
    private static final String BAR = "com.android.systemui.statusbar.";
    private final NotificationCountModule module;
    private final WeakHashMap<View, State> states = new WeakHashMap<>();
    private final Class<?> containerType;
    private final Class<?> receiverType;
    private final Field notificationArea;
    private final Field dispatcherField;
    private final Field injectField;
    private final Field enabledField;
    private final Field islandField;
    private final Field islandWidth;
    private final Method addReceiver;
    private final Method removeReceiver;
    private final Method getTint;
    private final Method paddingStart;
    private final Method paddingEnd;
    private final Method setMeasuredDimension;
    private int count;
    private boolean ready;
    private Integer iconColor;
    private IconColorCoordinator colors;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ColorHighlightController highlight = new ColorHighlightController(
            SystemClock::elapsedRealtime, new ColorHighlightController.Scheduler() {
                @Override public void schedule(Runnable task, long delayMillis) {
                    main.postDelayed(task, delayMillis);
                }
                @Override public void cancel(Runnable task) { main.removeCallbacks(task); }
            }, source -> { if (colors != null) colors.select(source); });

    StatusBarRenderer(NotificationCountModule module, HostAccess access) throws Throwable {
        this.module = module;
        containerType = access.type(BAR + "phone.NotificationIconContainer");
        Class<?> injector = access.type(BAR + "pipeline.shared.ui.binder.HomeStatusBarViewBinderInjector");
        notificationArea = HostAccess.field(injector, "mNotificationIconAreaInner");
        dispatcherField = HostAccess.field(injector, "darkIconDispatcher");
        injectField = HostAccess.field(containerType, "mInject");
        Class<?> inject = access.type(BAR + "phone.NotificationIconContainerInject");
        enabledField = HostAccess.field(inject, "showNotificationIcons");
        islandField = HostAccess.field(inject, "_islandMonitor");
        islandWidth = HostAccess.field(access.type(BAR + "IslandMonitor$NotificationContainerIslandMonitor"), "islandWidth");
        Class<?> dispatcherType = access.type("com.android.systemui.plugins.DarkIconDispatcher");
        receiverType = access.type("com.android.systemui.plugins.DarkIconDispatcher$DarkReceiver");
        addReceiver = HostAccess.method(dispatcherType, "addDarkReceiver", receiverType);
        removeReceiver = HostAccess.method(dispatcherType, "removeDarkReceiver", receiverType);
        getTint = HostAccess.method(dispatcherType, "getTint", Collection.class, View.class, int.class);
        paddingStart = HostAccess.method(containerType, "getActualPaddingStart");
        paddingEnd = HostAccess.method(containerType, "getActualPaddingEnd");
        setMeasuredDimension = HostAccess.method(View.class, "setMeasuredDimension", int.class, int.class);
    }

    void bind(Object injector) throws Throwable {
        Object area = notificationArea.get(injector);
        if (!(area instanceof ViewGroup view) || !containerType.isInstance(view)) return;
        State old = states.remove(view);
        if (old != null) old.dispose();
        Resources resources = view.getContext().getPackageManager()
                .getResourcesForApplication(module.getModuleApplicationInfo());
        State state = new State(view, injector, dispatcherField.get(injector), resources);
        states.put(view, state);
        view.addOnAttachStateChangeListener(state);
        state.updateDimensions();
        if (view.isAttachedToWindow()) state.attach();
        state.update();
        if (colors == null) {
            Context application = view.getContext().getApplicationContext();
            Context host = application != null ? application : view.getContext();
            colors = new IconColorCoordinator(source -> AppIconColorLoader.load(host, source),
                    Executors.newSingleThreadExecutor(task -> {
                        Thread thread = new Thread(() -> {
                            try { Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND); }
                            catch (SecurityException ignored) { }
                            task.run();
                        }, "HyperOSNotificationColor");
                        thread.setDaemon(true);
                        return thread;
                    }), main::post, SystemClock::elapsedRealtime, this::setIconColor);
        }
        colors.select(highlight.activeSource());
        view.requestLayout();
    }

    void unbind(Object injector) {
        for (State state : new ArrayList<>(states.values())) {
            if (state.owner.get() == injector) {
                View view = state.view.get();
                if (view != null) states.remove(view);
                state.dispose();
            }
        }
    }

    void setCount(int count, boolean ready, AppIconSource source, long arrival,
            boolean colorEnabled, boolean temporary, int seconds) throws Throwable {
        highlight.update(ready ? source : null, arrival, ready && colorEnabled, temporary, seconds);
        if (this.count == count && this.ready == ready) return;
        this.count = count;
        this.ready = ready;
        for (State state : new ArrayList<>(states.values())) {
            try { state.update(); } catch (Throwable error) { state.disable(error); }
            View view = state.view.get();
            if (view != null) view.requestLayout();
        }
    }

    void fallback() {
        ready = false;
        highlight.update(null, 0, false, false, 5);
        for (State state : new ArrayList<>(states.values())) {
            state.drawable.setCount(0);
            View view = state.view.get();
            if (view != null) {
                state.restoreDescription();
                view.requestLayout();
                view.invalidate();
            }
        }
    }

    private void setIconColor(Integer color) {
        iconColor = highlight.activeSource() != null ? color : null;
        for (State state : new ArrayList<>(states.values())) {
            try { state.updateTint(); } catch (Throwable error) { state.disable(error); }
        }
    }

    boolean suppressIcon(View icon) {
        State state = states.get(icon.getParent());
        if (state == null) return false;
        if (!state.drawable.isHealthy()) state.disable(new IllegalStateException("Drawable unavailable"));
        if (!state.active()) return false;
        return true;
    }

    void measure(View view, int widthSpec) throws Throwable {
        State state = states.get(view);
        if (state == null || !state.active()) return;
        boolean visible = state.enabled() && count > 0;
        int desired = visible ? state.slot + state.start() + state.end() : 0;
        HostAccess.call(setMeasuredDimension, view, View.resolveSize(desired, widthSpec), view.getMeasuredHeight());
    }

    void configuration(View view) throws Throwable {
        State state = states.get(view);
        if (state == null) return;
        state.updateDimensions();
        layoutAndRequest(view);
    }

    void layoutAndRequest(View view) throws Throwable {
        layout(view);
        if (states.containsKey(view)) view.requestLayout();
    }

    void layout(View view) throws Throwable {
        State state = states.get(view);
        if (state != null) state.update();
    }

    private final class State implements View.OnAttachStateChangeListener, ViewTreeObserver.OnPreDrawListener {
        final WeakReference<ViewGroup> view;
        final WeakReference<Object> owner;
        final CountDrawable drawable;
        final Object dispatcher;
        final Object receiver;
        CharSequence originalDescription;
        CharSequence ownedDescription;
        List<Rect> tintAreas = List.of();
        int tint = -1;
        int slot;
        int glyph;
        boolean attached;
        boolean receiverRegistered;
        boolean failed;
        boolean tintApplied;
        int lastSystemTint;
        Integer lastIconColor;

        State(ViewGroup view, Object owner, Object dispatcher, Resources resources) {
            this.view = new WeakReference<>(view);
            this.owner = new WeakReference<>(owner);
            this.dispatcher = dispatcher;
            originalDescription = view.getContentDescription();
            drawable = new CountDrawable(resources);
            receiver = Proxy.newProxyInstance(receiverType.getClassLoader(), new Class<?>[]{receiverType},
                    (proxy, method, args) -> {
                        if (method.getDeclaringClass() == Object.class) {
                            return SystemUiHooks.proxyObjectMethod(proxy, method.getName(), args);
                        }
                        try {
                            if (method.getName().equals("onDarkChanged")) {
                                List<Rect> copy = new ArrayList<>();
                                for (Object item : (Collection<?>) args[0]) copy.add(new Rect((Rect) item));
                                tintAreas = copy;
                                tint = (int) args[2];
                                updateTint();
                            }
                            // Contrast and image-specific tint callbacks do not alter our single-tone vectors.
                        } catch (Throwable error) { disable(error); }
                        return null;
                    });
        }

        boolean active() {
            return ready && attached && !failed && drawable.isHealthy();
        }

        boolean enabled() throws IllegalAccessException {
            ViewGroup current = view.get();
            return current != null && enabledField.getInt(injectField.get(current)) > 0;
        }

        int start() throws Throwable {
            return Math.max(0, Math.round((float) HostAccess.call(paddingStart, view.get())));
        }

        int end() throws Throwable {
            return Math.max(0, Math.round((float) HostAccess.call(paddingEnd, view.get())));
        }

        void updateDimensions() {
            ViewGroup current = view.get();
            if (current == null) return;
            Resources resources = current.getResources();
            float density = resources.getDisplayMetrics().density;
            // The supplied artwork has a 16dp canvas; preserve its circle and digit proportions.
            glyph = Math.max(1, Math.round(16 * density));
            slot = Math.max(glyph, Math.round(18 * density));
            drawable.setIconSize(glyph);
        }

        void updateTint() throws Throwable {
            ViewGroup current = view.get();
            if (current == null) return;
            int systemTint = (int) HostAccess.call(getTint, null, tintAreas, current, tint);
            Integer candidate = highlight.activeSource() != null ? iconColor : null;
            if (tintApplied && lastSystemTint == systemTint && Objects.equals(lastIconColor, candidate)) return;
            int color = ReadableIconColor.forSystemTint(candidate, systemTint);
            drawable.setTint(color);
            // Colored badges get solid black/white digits; monochrome ones keep the knockout.
            drawable.setGlyphColor(candidate != null ? ReadableIconColor.glyphOn(color) : null);
            lastSystemTint = systemTint;
            lastIconColor = candidate;
            tintApplied = true;
        }

        void update() throws Throwable {
            ViewGroup current = view.get();
            if (current == null) return;
            if (!drawable.isHealthy()) {
                disable(new IllegalStateException("Drawable unavailable"));
                return;
            }
            boolean visible = active() && enabled() && count > 0;
            int fromStart = start();
            int fromEnd = end();
            int width = current.getWidth();
            int height = current.getHeight();
            int left = current.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL
                    ? width - fromStart - slot + (slot - glyph) / 2 : fromStart + (slot - glyph) / 2;
            int top = (height - glyph) / 2;
            Object monitor = islandField.get(injectField.get(current));
            int available = monitor == null ? -1 : islandWidth.getInt(monitor);
            // Native monitor expresses the remaining span from layout start, including island translation.
            visible &= width >= fromStart + fromEnd + slot && height >= glyph
                    && (available < 0 || fromStart + slot <= available);
            drawable.setBounds(left, top, left + glyph, top + glyph);
            drawable.setCount(visible ? count : 0);
            updateTint();
            if (active() && enabled()) {
                CharSequence currentDescription = current.getContentDescription();
                if (ownedDescription == null || !java.util.Objects.equals(currentDescription, ownedDescription)) {
                    originalDescription = currentDescription;
                }
                ownedDescription = "通知总数：" + count;
                current.setContentDescription(ownedDescription);
            } else restoreDescription();
            current.invalidate();
        }

        void attach() throws Throwable {
            ViewGroup current = view.get();
            if (current == null || attached || failed) return;
            attached = true;
            current.getOverlay().add(drawable);
            current.getViewTreeObserver().addOnPreDrawListener(this);
            // Set this before add: the dispatcher invokes the receiver synchronously.
            receiverRegistered = true;
            HostAccess.call(addReceiver, dispatcher, receiver);
            update();
            current.requestLayout();
        }

        void detach() {
            attached = false;
            if (receiverRegistered) {
                receiverRegistered = false;
                try { HostAccess.call(removeReceiver, dispatcher, receiver); }
                catch (Throwable error) { logFailure(error); }
            }
            ViewGroup current = view.get();
            if (current != null) {
                if (current.getViewTreeObserver().isAlive()) current.getViewTreeObserver().removeOnPreDrawListener(this);
                current.getOverlay().remove(drawable);
                restoreDescription();
            }
        }

        void dispose() {
            detach();
            ViewGroup current = view.get();
            if (current != null) {
                current.removeOnAttachStateChangeListener(this);
                current.requestLayout();
                current.invalidate();
            }
        }

        void restoreDescription() {
            ViewGroup current = view.get();
            if (current != null && ownedDescription != null
                    && java.util.Objects.equals(current.getContentDescription(), ownedDescription)) {
                current.setContentDescription(originalDescription);
            }
            ownedDescription = null;
        }

        void disable(Throwable error) {
            if (failed) return;
            failed = true;
            drawable.setCount(0);
            detach();
            ViewGroup current = view.get();
            if (current != null) { current.requestLayout(); current.invalidate(); }
            logFailure(error);
        }

        private void logFailure(Throwable error) {
            module.log(Log.ERROR, NotificationCountModule.TAG,
                    "Status-bar overlay disabled: " + error.getClass().getSimpleName());
        }

        @Override public void onViewAttachedToWindow(View view) {
            try { attach(); } catch (Throwable error) { disable(error); }
        }

        @Override public void onViewDetachedFromWindow(View view) { detach(); }

        @Override public boolean onPreDraw() {
            highlight.expireIfNeeded();
            // Also handles containers without native child icons (for example only folded entries).
            if (!drawable.isHealthy()) disable(new IllegalStateException("Drawable unavailable"));
            return true;
        }
    }
}
