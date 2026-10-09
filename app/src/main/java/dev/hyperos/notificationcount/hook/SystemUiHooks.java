package dev.hyperos.notificationcount.hook;

import android.app.Notification;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.content.res.Configuration;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.view.View;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import dev.hyperos.notificationcount.NotificationCountModule;
import dev.hyperos.notificationcount.core.NotificationCounter;
import dev.hyperos.notificationcount.core.NotificationSnapshot;
import dev.hyperos.notificationcount.core.NotificationArrivals;
import dev.hyperos.notificationcount.core.AppIconSource;
import dev.hyperos.notificationcount.settings.FilterPreferences;
import io.github.libxposed.api.XposedInterface;

/** All hooks are process local, and notification snapshots are read on the main thread. */
public final class SystemUiHooks {
    private static final String COLLECTION = "com.android.systemui.statusbar.notification.collection.";
    private static final String BAR = "com.android.systemui.statusbar.";
    private static final String BINDER = BAR + "pipeline.shared.ui.binder.";
    private final NotificationCountModule module;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final HostAccess access;
    private final StatusBarRenderer renderer;
    private final NotificationClassifier classifier;
    private final NotificationArrivals arrivals = new NotificationArrivals();
    private SharedPreferences filterPreferences;
    // Framework listeners are weakly held. Keep this callback alive for the injected process.
    private final SharedPreferences.OnSharedPreferenceChangeListener filterListener = (preferences, key) -> {
        if (key == null || FilterPreferences.EXCLUDED_MASK.equals(key)
                || FilterPreferences.ICON_COLOR_ENABLED.equals(key)) requestRefresh();
    };
    private final List<XposedInterface.HookHandle> handles = new ArrayList<>();
    private final Class<?> pipelineType;
    private final Class<?> collectionType;
    private final Class<?> coordinatorType;
    private final Class<?> entryType;
    private final Field pipelineCollection;
    private final Field coordinatorUsers;
    private final Field userListeners;
    private final Field entrySbn;
    private final Field entryDismiss;
    private final Field entryCancellation;
    private final Object notDismissed;
    private final Method getAllNotifs;
    private final Method currentProfile;
    private final Class<?> userListenerType;
    private final Class<?> collectionListenerType;
    private final Class<?> beforeRenderListenerType;
    private final Method addCollectionListener;
    private final Method addBeforeRenderListener;
    private final Object collectionListener;
    private final Object beforeRenderListener;
    private final Object userListener;
    private Object pipeline;
    private Object collection;
    private Object users;
    private List<Object> listeners;
    private ListenerRegistration sourceRegistration = new ListenerRegistration();
    private boolean refreshPosted;

    public SystemUiHooks(NotificationCountModule module, ClassLoader loader) throws Throwable {
        this.module = module;
        access = new HostAccess(loader);
        classifier = new NotificationClassifier(access);
        pipelineType = access.type(COLLECTION + "NotifPipeline");
        collectionType = access.type(COLLECTION + "NotifCollection");
        coordinatorType = access.type(COLLECTION + "coordinator.HideNotifsForOtherUsersCoordinator");
        entryType = access.type(COLLECTION + "NotificationEntry");
        pipelineCollection = HostAccess.field(pipelineType, "mNotifCollection");
        coordinatorUsers = HostAccess.field(coordinatorType, "mLockscreenUserManager");
        Class<?> usersType = access.type(BAR + "NotificationLockscreenUserManagerImpl");
        userListeners = HostAccess.field(usersType, "mListeners");
        userListenerType = access.type(BAR + "NotificationLockscreenUserManager$UserChangedListener");
        collectionListenerType = access.type(COLLECTION + "notifcollection.NotifCollectionListener");
        beforeRenderListenerType = access.type(COLLECTION + "listbuilder.OnBeforeRenderListListener");
        addCollectionListener = HostAccess.method(pipelineType, "addCollectionListener", collectionListenerType);
        addBeforeRenderListener = HostAccess.method(pipelineType, "addOnBeforeRenderListListener", beforeRenderListenerType);
        currentProfile = HostAccess.method(usersType, "isCurrentProfile", int.class);
        getAllNotifs = HostAccess.method(pipelineType, "getAllNotifs");
        entrySbn = HostAccess.field(entryType, "mSbn");
        entryDismiss = HostAccess.field(entryType, "mDismissState");
        entryCancellation = HostAccess.field(entryType, "mCancellationReason");
        notDismissed = HostAccess.field(access.type(COLLECTION + "NotificationEntry$DismissState"),
                "NOT_DISMISSED").get(null);
        if (!StatusBarNotification.class.isAssignableFrom(entrySbn.getType())) {
            throw new IllegalStateException("Unsupported notification wrapper");
        }
        // Reuse proxy identity: native addIfAbsent must also deduplicate A -> B -> A reattachment.
        collectionListener = refreshListener(collectionListenerType);
        beforeRenderListener = refreshListener(beforeRenderListenerType);
        userListener = refreshListener(userListenerType);
        renderer = new StatusBarRenderer(module, access);
    }

    public void install() throws Throwable {
        // Resolve every required executable before registering hooks.
        Method attach = HostAccess.method(coordinatorType, "attach", pipelineType);
        Method dispatch = HostAccess.method(collectionType, "dispatchEventsAndRebuildList", String.class);
        Method dismiss = HostAccess.method(collectionType, "dismissNotifications", List.class, boolean.class);
        Method dismissAll = HostAccess.method(collectionType, "dismissAllNotifications", int.class);
        Class<?> binderType = access.type(BINDER + "HomeStatusBarViewBinderImpl");
        Field injector = HostAccess.field(binderType, "mInjector");
        Class<?> function = access.type("kotlin.jvm.functions.Function1");
        Method bind = HostAccess.method(binderType, "bind", access.type(BAR + "phone.PhoneStatusBarView"),
                access.type(BAR + "pipeline.shared.ui.viewmodel.HomeStatusBarViewModel"), function, function);
        Method unbind = HostAccess.method(access.type(BINDER + "HomeStatusBarViewBinderInjector"), "onUnbind");
        Class<?> container = access.type(BAR + "phone.NotificationIconContainer");
        Method measure = HostAccess.method(container, "onMeasure", int.class, int.class);
        Method layout = HostAccess.method(container, "onLayout", boolean.class,
                int.class, int.class, int.class, int.class);
        Method configuration = HostAccess.method(container, "onConfigurationChanged", Configuration.class);
        Method maxIcons = HostAccess.method(container, "setMaxIconsAmount", int.class);
        Method drawIcon = HostAccess.method(access.type(BAR + "StatusBarIconView"), "onDraw", Canvas.class);
        Class<?> monitor = access.type(BAR + "IslandMonitor$NotificationContainerIslandMonitor");
        Field monitorContainer = HostAccess.field(monitor, "container");
        Method island = HostAccess.method(monitor, "updateContainerSize", Rect.class, boolean.class, boolean.class);
        Class<?> utility = access.type(BAR + "notification.utils.NotificationUtil");
        Method fold = HostAccess.method(utility, "setFold", entryType, boolean.class);
        Class<?> headsUp = access.type(BAR + "notification.headsup.HeadsUpManagerImpl");
        Method pin = HostAccess.method(headsUp, "setEntryPinned",
                access.type(BAR + "notification.headsup.HeadsUpManagerImpl$HeadsUpEntry"),
                access.type(BAR + "notification.headsup.PinnedStatus"), String.class);
        Class<?> renderedType = access.type(BAR + "notification.domain.interactor.RenderNotificationListInteractor");
        Field sectionStyle = HostAccess.field(renderedType, "sectionStyleProvider");
        Method rendered = HostAccess.method(renderedType, "setRenderedList", List.class);
        try {
            filterPreferences = module.getRemotePreferences(FilterPreferences.GROUP);
            filterPreferences.registerOnSharedPreferenceChangeListener(filterListener);
            // These local-dismiss callers must reach the central refresh even while rendering is deferred.
            boolean dismissDeoptimized = module.deoptimize(dismiss);
            boolean clearDeoptimized = module.deoptimize(dismissAll);
            if (!dismissDeoptimized || !clearDeoptimized) {
                module.log(Log.WARN, NotificationCountModule.TAG, "Local-dismiss caller deoptimization unavailable");
            }
            after(attach, chain -> capture(chain.getArg(0), coordinatorUsers.get(chain.getThisObject())));
            after(dispatch, chain -> {
                if (chain.getThisObject() == collection) requestRefresh();
            });
            // Local dismissals can have no collection event and defer the before-render callback.
            // Keep an explicit refresh even when ART caller deoptimization is unavailable.
            after(dismiss, chain -> {
                if (chain.getThisObject() == collection) requestRefresh();
            });
            after(dismissAll, chain -> {
                if (chain.getThisObject() == collection) requestRefresh();
            });
            after(bind, chain -> renderer.bind(injector.get(chain.getThisObject())));
            after(unbind, chain -> renderer.unbind(chain.getThisObject()));
            after(measure, chain -> renderer.measure((View) chain.getThisObject(), (int) chain.getArg(0)));
            after(layout, chain -> renderer.layout((View) chain.getThisObject()));
            after(configuration, chain -> renderer.configuration((View) chain.getThisObject()));
            after(maxIcons, chain -> renderer.layoutAndRequest((View) chain.getThisObject()));
            after(island, chain -> renderer.layout((View) monitorContainer.get(chain.getThisObject())));
            after(fold, chain -> requestRefresh());
            after(pin, chain -> requestRefresh());
            after(rendered, chain -> {
                classifier.setSectionStyle(sectionStyle.get(chain.getThisObject()));
                requestRefresh();
            });
            handles.add(module.hook(drawIcon).setId("notification-count/draw")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(new ClippedDrawHook(renderer::suppressIcon)));
        } catch (Throwable error) {
            for (XposedInterface.HookHandle handle : handles) handle.unhook();
            handles.clear();
            if (filterPreferences != null) filterPreferences.unregisterOnSharedPreferenceChangeListener(filterListener);
            throw error;
        }
    }

    private void after(Method method, AfterHook.Action action) {
        handles.add(module.hook(method).setId("notification-count/" + method.getDeclaringClass().getName()
                        + "/" + method.getName()).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(new AfterHook(action, this::fail)));
    }

    @SuppressWarnings("unchecked")
    private void capture(Object newPipeline, Object newUsers) throws Throwable {
        if (Looper.myLooper() != main.getLooper()) {
            main.post(() -> {
                try { capture(newPipeline, newUsers); } catch (Throwable error) { fail(error); }
            });
            return;
        }
        if (pipeline == newPipeline && users == newUsers && listeners != null) {
            ensureSourceListeners();
            refreshNow();
            return;
        }
        Object newCollection = pipelineCollection.get(newPipeline);
        List<Object> newListeners = (List<Object>) userListeners.get(newUsers);
        boolean newSource = pipeline != newPipeline;
        if (!newListeners.contains(userListener)) newListeners.add(userListener);
        if (listeners != null && listeners != newListeners) listeners.remove(userListener);
        pipeline = newPipeline;
        users = newUsers;
        collection = newCollection;
        listeners = newListeners;
        if (newSource) {
            sourceRegistration = new ListenerRegistration();
            arrivals.clear();
        }
        ensureSourceListeners();
        refreshNow();
    }

    private void ensureSourceListeners() throws Throwable {
        // Preserve completed registrations across a partial failure and retry only the missing step.
        sourceRegistration.ensure(
                () -> HostAccess.call(addCollectionListener, pipeline, collectionListener),
                () -> HostAccess.call(addBeforeRenderListener, pipeline, beforeRenderListener));
    }

    private Object refreshListener(Class<?> listenerType) {
        return Proxy.newProxyInstance(listenerType.getClassLoader(), new Class<?>[]{listenerType},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return proxyObjectMethod(proxy, method.getName(), args);
                    }
                    requestRefresh();
                    return null;
                });
    }

    private void requestRefresh() {
        if (Looper.myLooper() != main.getLooper()) {
            main.post(this::requestRefresh);
            return;
        }
        if (pipeline == null || refreshPosted) return;
        refreshPosted = true;
        main.post(() -> {
            refreshPosted = false;
            try {
                ensureSourceListeners();
                refreshNow();
            } catch (Throwable error) { fail(error); }
        });
    }

    @SuppressWarnings("deprecation") // Public SDK accessor; UserHandle.getIdentifier is not in SDK 37 stubs.
    private void refreshNow() throws Throwable {
        int excludedMask = FilterPreferences.read(filterPreferences);
        boolean colorEnabled = FilterPreferences.readIconColor(filterPreferences);
        Collection<?> current = (Collection<?>) HostAccess.call(getAllNotifs, pipeline);
        List<NotificationSnapshot> snapshots = new ArrayList<>(current.size());
        // getAllNotifs is a live view, so never retain it or its entries after this main-thread read.
        for (Object entry : new ArrayList<>(current)) {
            if (!entryType.isInstance(entry)) throw new IllegalStateException("Unexpected collection entry");
            StatusBarNotification sbn = (StatusBarNotification) entrySbn.get(entry);
            int userId = sbn.getUserId();
            boolean profile = (boolean) HostAccess.call(currentProfile, users, userId);
            snapshots.add(new NotificationSnapshot(sbn.getKey(), userId, sbn.getGroupKey(),
                    (sbn.getNotification().flags & Notification.FLAG_GROUP_SUMMARY) != 0, profile,
                    entryDismiss.get(entry) != notDismissed, entryCancellation.getInt(entry) != -1,
                    classifier.classify(entry, sbn, excludedMask),
                    colorEnabled ? sbn.getPackageName() : null,
                    colorEnabled ? sbn.getPostTime() : 0));
        }
        List<NotificationSnapshot> counted = NotificationCounter.countedNotifications(snapshots, excludedMask);
        AppIconSource source = null;
        if (colorEnabled) {
            arrivals.observe(snapshots);
            NotificationSnapshot newest = arrivals.newest(counted);
            if (newest != null && newest.packageName != null) {
                source = new AppIconSource(newest.packageName, newest.userId);
            }
        } else arrivals.clear();
        renderer.setCount(counted.size(), true, source);
    }

    private void fail(Throwable error) {
        module.log(Log.ERROR, NotificationCountModule.TAG,
                "Keeping original icons after module error: " + error.getClass().getSimpleName());
        if (Looper.myLooper() == main.getLooper()) renderer.fallback();
        else main.post(renderer::fallback);
    }

    static Object proxyObjectMethod(Object proxy, String name, Object[] args) {
        return switch (name) {
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            case "toString" -> "HyperOSNotificationCountListener";
            default -> null;
        };
    }
}
