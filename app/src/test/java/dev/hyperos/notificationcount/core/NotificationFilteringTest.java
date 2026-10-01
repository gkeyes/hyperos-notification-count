package dev.hyperos.notificationcount.core;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import org.junit.Test;

public class NotificationFilteringTest {
    @Test
    public void legacySnapshotHasNoTypeBits() {
        NotificationSnapshot snapshot = new NotificationSnapshot(
                "legacy", 10, null, false, true, false, false);
        assertEquals(0, snapshot.typeMask);
        assertEquals(1, count(-1, snapshot));
    }

    @Test
    public void defaultCountAndZeroMaskKeepEveryType() {
        for (NotificationType type : NotificationType.values()) {
            NotificationSnapshot snapshot = active("typed", 10, null, false, type.bit);
            assertEquals(type.key, 1, NotificationCounter.count(Arrays.asList(snapshot)));
            assertEquals(type.key, 1, count(0, snapshot));
        }
    }

    @Test
    public void eachEnabledTypeExcludesOnlyItsMatchingEntry() {
        for (NotificationType type : NotificationType.values()) {
            assertEquals(type.key, 1, count(type.bit,
                    active("typed", 10, null, false, type.bit),
                    active("ordinary", 10, null, false, 0)));
        }
    }

    @Test
    public void overlappingTypesMatchAnyEnabledFilter() {
        NotificationSnapshot ongoingService = active("service", 10, null, false,
                NotificationType.ONGOING_EVENT.bit | NotificationType.NO_CLEAR.bit
                        | NotificationType.FOREGROUND_SERVICE.bit);
        assertEquals(0, count(NotificationType.ONGOING_EVENT.bit, ongoingService));
        assertEquals(0, count(NotificationType.NO_CLEAR.bit, ongoingService));
        assertEquals(0, count(NotificationType.FOREGROUND_SERVICE.bit, ongoingService));
        assertEquals(1, count(NotificationType.MEDIA.bit, ongoingService));
    }

    @Test
    public void multipleEnabledFiltersExcludeEitherType() {
        assertEquals(1, count(NotificationType.MEDIA.bit | NotificationType.CALL.bit,
                active("media", 10, null, false, NotificationType.MEDIA.bit),
                active("call", 10, null, false, NotificationType.CALL.bit),
                active("ordinary", 10, null, false, 0)));
    }

    @Test
    public void filteringEveryChildDoesNotRestoreItsSummary() {
        assertEquals(0, count(NotificationType.MEDIA.bit,
                active("summary", 10, "group", true, 0),
                active("child-one", 10, "group", false, NotificationType.MEDIA.bit),
                active("child-two", 10, "group", false, NotificationType.MEDIA.bit)));
    }

    @Test
    public void filteringOneChildKeepsOtherChildrenWithoutTheirSummary() {
        assertEquals(1, count(NotificationType.MEDIA.bit,
                active("summary", 10, "group", true, 0),
                active("media-child", 10, "group", false, NotificationType.MEDIA.bit),
                active("ordinary-child", 10, "group", false, 0)));
    }

    @Test
    public void ineligibleChildrenAllowAnEligibleOrphanSummary() {
        assertEquals(1, count(NotificationType.MEDIA.bit,
                active("summary", 10, "group", true, 0),
                new NotificationSnapshot("canceled", 10, "group", false,
                        true, false, true, NotificationType.MEDIA.bit),
                new NotificationSnapshot("dismissed", 10, "group", false,
                        true, true, false, NotificationType.MEDIA.bit),
                new NotificationSnapshot("other-profile", 10, "group", false,
                        false, false, false, NotificationType.MEDIA.bit)));
    }

    @Test
    public void anOrphanSummaryCanBeFilteredByItsOwnType() {
        assertEquals(0, count(NotificationType.SILENT.bit,
                active("summary", 10, "group", true, NotificationType.SILENT.bit)));
    }

    @Test
    public void filteringDoesNotPairNullGroupsOrDifferentUsers() {
        assertEquals(2, count(NotificationType.MEDIA.bit,
                active("ungrouped-summary", 10, null, true, 0),
                active("personal-summary", 0, "group", true, 0),
                active("work-summary", 10, "group", true, 0),
                active("ungrouped-child", 10, null, false, NotificationType.MEDIA.bit),
                active("work-child", 10, "group", false, NotificationType.MEDIA.bit)));
    }

    @Test
    public void sameKeyUpdatesReplaceThePreviousTypes() {
        NotificationSnapshot ordinary = active("same", 10, null, false, 0);
        NotificationSnapshot media = active("same", 10, null, false, NotificationType.MEDIA.bit);
        assertEquals(0, count(NotificationType.MEDIA.bit, ordinary, media));
        assertEquals(1, count(NotificationType.MEDIA.bit, media, ordinary));
    }

    @Test
    public void onlyTheLatestGroupPairsBeforeTypeFiltering() {
        assertEquals(1, count(NotificationType.MEDIA.bit,
                active("summary-old", 10, "old", true, 0),
                active("summary-new", 10, "new", true, 0),
                active("same-child", 10, "old", false, 0),
                active("same-child", 10, "new", false, NotificationType.MEDIA.bit)));
    }

    private static int count(int excludedMask, NotificationSnapshot... snapshots) {
        return NotificationCounter.count(Arrays.asList(snapshots), excludedMask);
    }

    private static NotificationSnapshot active(
            String key, int userId, String groupKey, boolean summary, int typeMask) {
        return new NotificationSnapshot(
                key, userId, groupKey, summary, true, false, false, typeMask);
    }
}
