package dev.hyperos.notificationcount.hook;

import android.app.Notification;
import android.service.notification.StatusBarNotification;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;

import dev.hyperos.notificationcount.core.NotificationType;

/** ROM-native predicates verified against the extracted SystemUI; evaluates only enabled filters. */
final class NotificationClassifier {
    private static final String NOTIFICATION = "com.android.systemui.statusbar.notification.";
    private static final String COLLECTION = NOTIFICATION + "collection.";
    private final Class<?> wrapperType;
    private final Class<?> sectionStyleType;
    private final Field focus;
    private final Field promoted;
    private final Field folded;
    private final Field attachState;
    private final Field attachedSection;
    private final Field bucket;
    private final Field sectioner;
    private final Field silentSections;
    private final Field highPriorityProvider;
    private final Method updatable;
    private final Method persistent;
    private final Method clearable;
    private final Method pinned;
    private final Method media;
    private final Method highPriorityConversation;
    private Object sectionStyle;

    NotificationClassifier(HostAccess access) throws Throwable {
        wrapperType = access.type(NOTIFICATION + "ExpandedNotification");
        Class<?> entryType = access.type(COLLECTION + "NotificationEntry");
        Class<?> pipelineEntry = access.type(COLLECTION + "PipelineEntry");
        Class<?> focusUtils = access.type(NOTIFICATION + "utils.FocusUtils");
        Class<?> notificationUtil = access.type(NOTIFICATION + "utils.NotificationUtil");
        focus = HostAccess.field(wrapperType, "mIsFocusNotification");
        promoted = HostAccess.field(wrapperType, "mIsPromotedOngoing");
        folded = HostAccess.field(wrapperType, "mIsFold");
        updatable = HostAccess.method(focusUtils, "isUpdatableFocusNotification", Notification.class);
        persistent = HostAccess.method(wrapperType, "isPersistent");
        clearable = HostAccess.method(entryType, "isClearable");
        pinned = HostAccess.method(entryType, "isRowPinned");
        media = HostAccess.method(notificationUtil, "isMiuiMediaNotification", entryType);
        attachState = HostAccess.field(pipelineEntry, "attachState");
        Class<?> stateType = access.type(COLLECTION + "ListAttachState");
        attachedSection = HostAccess.field(stateType, "section");
        Class<?> sectionType = access.type(COLLECTION + "listbuilder.NotifSection");
        bucket = HostAccess.field(sectionType, "bucket");
        sectioner = HostAccess.field(sectionType, "sectioner");
        sectionStyleType = access.type(COLLECTION + "provider.SectionStyleProvider");
        silentSections = HostAccess.field(sectionStyleType, "silentSections");
        highPriorityProvider = HostAccess.field(sectionStyleType, "highPriorityProvider");
        Class<?> priorityType = access.type(COLLECTION + "provider.HighPriorityProvider");
        highPriorityConversation = HostAccess.method(priorityType, "isHighPriorityConversation", pipelineEntry);
    }

    void setSectionStyle(Object provider) {
        if (!sectionStyleType.isInstance(provider)) throw new IllegalArgumentException("Unexpected section provider");
        sectionStyle = provider;
    }

    int classify(Object entry, StatusBarNotification sbn, int requested) throws Throwable {
        if (requested == 0) return 0;
        if (!wrapperType.isInstance(sbn)) throw new IllegalStateException("Unexpected notification wrapper");
        Notification notification = sbn.getNotification();
        int result = NotificationPayloadTypes.publicTypes(notification, requested);
        if (enabled(requested, NotificationType.FOCUS) && focus.getBoolean(sbn)) result |= NotificationType.FOCUS.bit;
        if (enabled(requested, NotificationType.PROMOTED_ONGOING) && promoted.getBoolean(sbn)) result |= NotificationType.PROMOTED_ONGOING.bit;
        if (enabled(requested, NotificationType.FOLDED) && folded.getBoolean(sbn)) result |= NotificationType.FOLDED.bit;
        if (enabled(requested, NotificationType.UPDATABLE_FOCUS)
                && (boolean) HostAccess.call(updatable, null, notification)) result |= NotificationType.UPDATABLE_FOCUS.bit;
        if (enabled(requested, NotificationType.PERSISTENT)
                && (boolean) HostAccess.call(persistent, sbn)) result |= NotificationType.PERSISTENT.bit;
        if (enabled(requested, NotificationType.NOT_CLEARABLE)
                && !(boolean) HostAccess.call(clearable, entry)) result |= NotificationType.NOT_CLEARABLE.bit;
        if (enabled(requested, NotificationType.HEADS_UP_PINNED)
                && (boolean) HostAccess.call(pinned, entry)) result |= NotificationType.HEADS_UP_PINNED.bit;
        if (enabled(requested, NotificationType.MEDIA)
                && (boolean) HostAccess.call(media, null, entry)) result |= NotificationType.MEDIA.bit;
        if (enabled(requested, NotificationType.SILENT) && isSilent(entry)) result |= NotificationType.SILENT.bit;
        return result;
    }

    private boolean isSilent(Object entry) throws Throwable {
        Object section = attachedSection.get(attachState.get(entry));
        // The host model also defaults unsectioned entries to silent.
        if (section == null) return true;
        if (sectionStyle == null) throw new IllegalStateException("Notification sections not ready");
        if (bucket.getInt(section) == 4) {
            return !(boolean) HostAccess.call(highPriorityConversation,
                    highPriorityProvider.get(sectionStyle), entry);
        }
        return ((Set<?>) silentSections.get(sectionStyle)).contains(sectioner.get(section));
    }

    private static boolean enabled(int mask, NotificationType type) { return (mask & type.bit) != 0; }
}
