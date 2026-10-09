package dev.hyperos.notificationcount.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Main-thread arrival order. Updating an existing key never makes it a new notification. */
public final class NotificationArrivals {
    private final Map<String, Long> order = new HashMap<>();
    private long sequence;

    public void observe(Collection<NotificationSnapshot> snapshots) {
        Set<String> live = new HashSet<>();
        List<NotificationSnapshot> added = new ArrayList<>();
        for (NotificationSnapshot item : snapshots) {
            if (live.add(item.key) && !order.containsKey(item.key)) added.add(item);
        }
        order.keySet().retainAll(live);
        // Seed an existing collection by system post time, with a deterministic tie break.
        added.sort(Comparator.comparingLong((NotificationSnapshot item) -> item.postTime)
                .thenComparing(item -> item.key));
        for (NotificationSnapshot item : added) order.put(item.key, ++sequence);
    }

    public NotificationSnapshot newest(Collection<NotificationSnapshot> counted) {
        NotificationSnapshot newest = null;
        long newestOrder = Long.MIN_VALUE;
        for (NotificationSnapshot item : counted) {
            long arrival = arrivalOf(item);
            if (arrival > newestOrder) {
                newest = item;
                newestOrder = arrival;
            }
        }
        return newest;
    }

    public long arrivalOf(NotificationSnapshot item) {
        return item == null ? 0 : order.getOrDefault(item.key, 0L);
    }

    public void clear() {
        order.clear();
        // Keep ordinals monotonic across pipeline replacement, so a highlight token cannot collide.
    }
}
