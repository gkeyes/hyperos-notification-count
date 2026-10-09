package dev.hyperos.notificationcount.core;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class NotificationColorSourceTest {
    private final NotificationArrivals arrivals = new NotificationArrivals();

    @Test public void pipelineReplacementCannotReuseAnOldHighlightArrivalToken() {
        NotificationSnapshot first = item("first", 100, 0, null, false, true, false, 0);
        newest(0, first);
        long old = arrivals.arrivalOf(first);
        arrivals.clear();
        newest(0, first);
        assertTrue(arrivals.arrivalOf(first) > old);
    }

    @Test public void seedsByPostTimeRatherThanCollectionIterationOrder() {
        NotificationSnapshot latest = item("latest", 200, 0, null, false, true, false, 0);
        NotificationSnapshot older = item("older", 100, 0, null, false, true, false, 0);
        assertSame(latest, newest(0, latest, older));
    }

    @Test public void progressUpdatesDoNotTakeColorFromANewerNotification() {
        NotificationSnapshot download = item("download", 100, 0, null, false, true, false, 0);
        NotificationSnapshot message = item("message", 200, 0, null, false, true, false, 0);
        assertSame(message, newest(0, download, message));
        NotificationSnapshot updated = item("download", 900, 0, null, false, true, false, 0);
        assertSame(message, newest(0, updated, message));
        assertSame(updated, newest(0, updated));
    }

    @Test public void aNewKeyWinsEvenIfItsTimestampIsEarlier() {
        NotificationSnapshot original = item("old", 900, 0, null, false, true, false, 0);
        newest(0, original);
        NotificationSnapshot added = item("new", 10, 0, null, false, true, false, 0);
        assertSame(added, newest(0, original, added));
    }

    @Test public void filteredDismissedAndOtherProfileEntriesCannotProvideColor() {
        NotificationSnapshot kept = item("kept", 100, 0, null, false, true, false, 0);
        NotificationSnapshot filtered = item("filtered", 200, 0, null, false, true, false, NotificationType.MEDIA.bit);
        NotificationSnapshot dismissed = item("dismissed", 300, 0, null, false, true, true, 0);
        NotificationSnapshot other = item("other", 400, 10, null, false, false, false, 0);
        assertSame(kept, newest(NotificationType.MEDIA.bit, kept, filtered, dismissed, other));
        assertSame(filtered, newest(0, kept, filtered, dismissed, other));
        assertEquals(2, NotificationCounter.count(Arrays.asList(kept, filtered, dismissed, other)));
    }

    @Test public void groupedSummaryNeverReplacesAnExcludedChildColor() {
        NotificationSnapshot kept = item("kept", 100, 0, null, false, true, false, 0);
        NotificationSnapshot child = item("child", 200, 0, "group", false, true, false, NotificationType.MEDIA.bit);
        NotificationSnapshot summary = item("summary", 300, 0, "group", true, true, false, 0);
        assertSame(child, newest(0, kept, child, summary));
        assertSame(kept, newest(NotificationType.MEDIA.bit, kept, child, summary));
        assertEquals(1, NotificationCounter.count(Arrays.asList(kept, child, summary), NotificationType.MEDIA.bit));
    }

    @Test public void removalAndRepostOfTheSameKeyIsANewArrival() {
        NotificationSnapshot first = item("first", 900, 0, null, false, true, false, 0);
        NotificationSnapshot second = item("second", 1000, 0, null, false, true, false, 0);
        newest(0, first, second);
        assertSame(second, newest(0, second));
        NotificationSnapshot repost = item("first", 100, 0, null, false, true, false, 0);
        assertSame(repost, newest(0, second, repost));
        assertNull(newest(0));
    }

    private NotificationSnapshot newest(int mask, NotificationSnapshot... items) {
        List<NotificationSnapshot> all = Arrays.asList(items);
        arrivals.observe(all);
        return arrivals.newest(NotificationCounter.countedNotifications(all, mask));
    }

    private NotificationSnapshot item(String key, long time, int user, String group,
            boolean summary, boolean profile, boolean dismissed, int mask) {
        return new NotificationSnapshot(key, user, group, summary, profile, dismissed,
                false, mask, "app." + key, time);
    }
}
