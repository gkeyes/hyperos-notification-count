package dev.hyperos.notificationcount.core;

import java.util.Objects;

/** Immutable notification metadata needed for counting. */
public final class NotificationSnapshot {
    public final String key;
    public final int userId;
    public final String groupKey;
    public final boolean summary;
    public final boolean currentProfile;
    public final boolean dismissed;
    public final boolean canceled;
    public final int typeMask;

    public NotificationSnapshot(
            String key,
            int userId,
            String groupKey,
            boolean summary,
            boolean currentProfile,
            boolean dismissed,
            boolean canceled) {
        this(key, userId, groupKey, summary, currentProfile, dismissed, canceled, 0);
    }

    public NotificationSnapshot(
            String key,
            int userId,
            String groupKey,
            boolean summary,
            boolean currentProfile,
            boolean dismissed,
            boolean canceled,
            int typeMask) {
        this.key = Objects.requireNonNull(key, "key");
        this.userId = userId;
        this.groupKey = groupKey;
        this.summary = summary;
        this.currentProfile = currentProfile;
        this.dismissed = dismissed;
        this.canceled = canceled;
        this.typeMask = typeMask;
    }
}
