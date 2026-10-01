package dev.hyperos.notificationcount.core;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Counts effective notification entries without counting group summaries twice. */
public final class NotificationCounter {
    private NotificationCounter() {
    }

    public static int count(Collection<NotificationSnapshot> snapshots) {
        return count(snapshots, 0);
    }

    /** Excludes an entry if any of its type bits matches an enabled filter. */
    public static int count(Collection<NotificationSnapshot> snapshots, int excludedMask) {
        Map<String, NotificationSnapshot> latestByKey = new LinkedHashMap<>();
        for (NotificationSnapshot snapshot : snapshots) {
            latestByKey.put(snapshot.key, snapshot);
        }

        // Pair summaries before type filtering. Excluding a real child must not make
        // its summary contribute a replacement count.
        Set<GroupIdentity> groupsWithChildren = new HashSet<>();
        for (NotificationSnapshot snapshot : latestByKey.values()) {
            if (isEligible(snapshot) && !snapshot.summary && snapshot.groupKey != null) {
                groupsWithChildren.add(new GroupIdentity(snapshot.userId, snapshot.groupKey));
            }
        }

        int count = 0;
        for (NotificationSnapshot snapshot : latestByKey.values()) {
            if (!isEligible(snapshot)) {
                continue;
            }
            if ((snapshot.typeMask & excludedMask) != 0) {
                continue;
            }
            if (snapshot.summary && snapshot.groupKey != null
                    && groupsWithChildren.contains(
                            new GroupIdentity(snapshot.userId, snapshot.groupKey))) {
                continue;
            }
            count++;
        }
        return count;
    }

    private static boolean isEligible(NotificationSnapshot snapshot) {
        return snapshot.currentProfile && !snapshot.dismissed && !snapshot.canceled;
    }

    private static final class GroupIdentity {
        private final int userId;
        private final String groupKey;

        private GroupIdentity(int userId, String groupKey) {
            this.userId = userId;
            this.groupKey = groupKey;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof GroupIdentity)) {
                return false;
            }
            GroupIdentity group = (GroupIdentity) other;
            return userId == group.userId && groupKey.equals(group.groupKey);
        }

        @Override
        public int hashCode() {
            return 31 * Integer.hashCode(userId) + groupKey.hashCode();
        }
    }
}
