package dev.hyperos.notificationcount.core;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class NotificationCounterTest {
    @Test
    public void emptyCollectionCountsZero() {
        assertEquals(0, NotificationCounter.count(Collections.emptyList()));
    }

    @Test
    public void countsDistinctUngroupedNotifications() {
        assertEquals(3, count(
                active("first", 10, null, false),
                active("second", 10, null, false),
                active("third", 10, null, false)));
    }

    @Test
    public void countsChildrenInsteadOfTheirGroupSummary() {
        assertEquals(3, count(
                active("summary", 10, "messages", true),
                active("child-one", 10, "messages", false),
                active("child-two", 10, "messages", false),
                active("other-app", 10, null, false)));
    }

    @Test
    public void keepsAnOrphanSummary() {
        assertEquals(1, count(active("summary", 10, "messages", true)));
    }

    @Test
    public void countsChildrenWithoutASummary() {
        assertEquals(2, count(
                active("child-one", 10, "messages", false),
                active("child-two", 10, "messages", false)));
    }

    @Test
    public void nullGroupKeysDoNotPairUnrelatedEntries() {
        assertEquals(3, count(
                active("summary-one", 10, null, true),
                active("summary-two", 10, null, true),
                active("ungrouped", 10, null, false)));
    }

    @Test
    public void identicalGroupKeysInDifferentUsersRemainSeparate() {
        assertEquals(2, count(
                active("personal-summary", 0, "messages", true),
                active("work-child", 10, "messages", false)));
    }

    @Test
    public void excludesOtherProfilesDismissedAndCanceledEntries() {
        assertEquals(1, count(
                active("kept", 10, null, false),
                snapshot("other-profile", 0, null, false, false, false, false),
                snapshot("dismissed", 10, null, false, true, true, false),
                snapshot("canceled", 10, null, false, true, false, true)));
    }

    @Test
    public void filteredChildrenDoNotSuppressAnEligibleSummary() {
        assertEquals(1, count(
                active("summary", 10, "messages", true),
                snapshot("other-profile", 10, "messages", false, false, false, false),
                snapshot("dismissed", 10, "messages", false, true, true, false),
                snapshot("canceled", 10, "messages", false, true, false, true)));
    }

    @Test
    public void anIneligibleSummaryDoesNotRemoveItsEligibleChildren() {
        assertEquals(2, count(
                snapshot("summary", 10, "messages", true, true, true, false),
                active("child-one", 10, "messages", false),
                active("child-two", 10, "messages", false)));
    }

    @Test
    public void duplicateKeysCountOnlyOnce() {
        assertEquals(1, count(
                active("same-key", 10, null, false),
                active("same-key", 10, null, false)));
    }

    @Test
    public void aCanceledUpdateReplacesThePreviouslyEligibleEntry() {
        assertEquals(0, count(
                active("same-key", 10, null, false),
                snapshot("same-key", 10, null, false, true, false, true)));
    }

    @Test
    public void aDismissedUpdateReplacesThePreviouslyEligibleEntry() {
        assertEquals(0, count(
                active("same-key", 10, null, false),
                snapshot("same-key", 10, null, false, true, true, false)));
    }

    @Test
    public void anEligibleUpdateRestoresAPreviouslyCanceledEntry() {
        assertEquals(1, count(
                snapshot("same-key", 10, null, false, true, false, true),
                active("same-key", 10, null, false)));
    }

    @Test
    public void aGroupUpdateUsesTheLatestGroupForSummaryPairing() {
        assertEquals(2, count(
                active("summary-a", 10, "group-a", true),
                active("summary-b", 10, "group-b", true),
                active("stable-child", 10, "group-a", false),
                active("updated-child", 10, "group-a", false),
                active("updated-child", 10, "group-b", false)));
    }

    private static int count(NotificationSnapshot... snapshots) {
        return NotificationCounter.count(Arrays.asList(snapshots));
    }

    private static NotificationSnapshot active(
            String key, int userId, String groupKey, boolean summary) {
        return snapshot(key, userId, groupKey, summary, true, false, false);
    }

    private static NotificationSnapshot snapshot(
            String key,
            int userId,
            String groupKey,
            boolean summary,
            boolean currentProfile,
            boolean dismissed,
            boolean canceled) {
        return new NotificationSnapshot(
                key, userId, groupKey, summary, currentProfile, dismissed, canceled);
    }
}
