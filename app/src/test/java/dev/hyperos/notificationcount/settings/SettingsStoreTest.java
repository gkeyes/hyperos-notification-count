package dev.hyperos.notificationcount.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.SharedPreferences;

import java.util.Arrays;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import dev.hyperos.notificationcount.core.NotificationType;
import org.junit.Test;

public class SettingsStoreTest {
    private static final Executor DIRECT = Runnable::run;

    @Test public void temporaryModeAndDurationPersistAcrossFreshCachesAndFilterReset() {
        SettingsTestDoubles.OptimisticPreferences remote = new SettingsTestDoubles.OptimisticPreferences();
        SettingsStore store = connected(remote);
        assertFalse(store.state().temporaryColor);
        assertEquals(5, store.state().colorDurationSeconds);
        store.setIconColorEnabled(true);
        store.setTemporaryColor(true);
        for (int duration : new int[]{1,3,5,10,15}) {
            store.setColorDurationSeconds(duration);
            assertEquals(duration, connected(remote.freshConnection()).state().colorDurationSeconds);
        }
        store.setExcluded(NotificationType.MEDIA, true);
        store.reset();
        SettingsStore fresh = connected(remote.freshConnection());
        assertTrue(fresh.state().iconColorEnabled);
        assertTrue(fresh.state().temporaryColor);
        assertEquals(15, fresh.state().colorDurationSeconds);
        assertEquals(0, fresh.state().mask);
    }

    @Test public void failedDurationSaveRestoresAllConfirmedColorSettingsAndRetryKeepsThem() {
        SettingsTestDoubles.OptimisticPreferences remote = new SettingsTestDoubles.OptimisticPreferences();
        SettingsStore store = connected(remote);
        store.setIconColorEnabled(true);
        store.setTemporaryColor(true);
        store.setColorDurationSeconds(3);
        remote.queueCommitResults(false, false);
        store.setColorDurationSeconds(15);
        assertState(store, 0, SettingsStore.Status.SAVE_FAILED);
        assertTrue(store.state().iconColorEnabled);
        assertTrue(store.state().temporaryColor);
        assertEquals(3, store.state().colorDurationSeconds);
        assertEquals(3, FilterPreferences.readColorDuration(remote.preferences));
        store.retry();
        assertState(store, 0, SettingsStore.Status.READY);
        assertEquals(3, connected(remote.freshConnection()).state().colorDurationSeconds);
    }

    @Test public void badgeStyleDefaultsPersistAndSurviveFilterReset() {
        SettingsTestDoubles.OptimisticPreferences remote = new SettingsTestDoubles.OptimisticPreferences();
        SettingsStore store = connected(remote);
        assertEquals(FilterPreferences.DIGIT_COLOR_AUTO, store.state().digitColor);
        assertEquals(45, store.state().badgeContrast);
        assertEquals(FilterPreferences.WEIGHT_NORMAL, store.state().digitWeight);
        store.setDigitColor(FilterPreferences.DIGIT_COLOR_WHITE);
        store.setBadgeContrast(70);
        store.setDigitWeight(FilterPreferences.WEIGHT_MEDIUM);
        store.setExcluded(NotificationType.MEDIA, true);
        store.reset();
        SettingsStore fresh = connected(remote.freshConnection());
        assertEquals(FilterPreferences.DIGIT_COLOR_WHITE, fresh.state().digitColor);
        assertEquals(70, fresh.state().badgeContrast);
        assertEquals(FilterPreferences.WEIGHT_MEDIUM, fresh.state().digitWeight);
        assertEquals(0, fresh.state().mask);
        // Unknown stored values fall back to defaults instead of breaking the badge.
        fresh.setBadgeContrast(12);
        assertEquals(45, connected(remote.freshConnection()).state().badgeContrast);
    }

    @Test public void failedStyleSaveRestoresEveryConfirmedOption() {
        SettingsTestDoubles.OptimisticPreferences remote = new SettingsTestDoubles.OptimisticPreferences();
        SettingsStore store = connected(remote);
        store.setDigitWeight(FilterPreferences.WEIGHT_BOLD);
        store.setDigitColor(FilterPreferences.DIGIT_COLOR_BLACK);
        remote.queueCommitResults(false, false);
        store.setBadgeContrast(30);
        assertState(store, 0, SettingsStore.Status.SAVE_FAILED);
        assertEquals(45, store.state().badgeContrast);
        assertEquals(FilterPreferences.WEIGHT_BOLD, store.state().digitWeight);
        assertEquals(FilterPreferences.DIGIT_COLOR_BLACK, store.state().digitColor);
        assertEquals(45, FilterPreferences.readBadgeContrast(remote.preferences));
        store.retry();
        assertState(store, 0, SettingsStore.Status.READY);
        assertEquals(FilterPreferences.WEIGHT_BOLD, connected(remote.freshConnection()).state().digitWeight);
    }

    @Test public void iconColorDefaultsOffAndPersistsIndependentlyOfFilterReset() {
        SettingsTestDoubles.OptimisticPreferences remote = new SettingsTestDoubles.OptimisticPreferences();
        SettingsStore store = connected(remote);
        assertFalse(store.state().iconColorEnabled);
        store.setIconColorEnabled(true);
        store.setExcluded(NotificationType.MEDIA, true);
        store.reset();
        assertState(store, 0, SettingsStore.Status.READY);
        assertTrue(store.state().iconColorEnabled);
        SettingsTestDoubles.OptimisticPreferences reopenedRemote = remote.freshConnection();
        SettingsStore reopened = connected(reopenedRemote);
        assertTrue(reopened.state().iconColorEnabled);
        reopened.setIconColorEnabled(false);
        assertFalse(connected(reopenedRemote.freshConnection()).state().iconColorEnabled);
    }

    @Test public void aFailedColorSaveRollsBackBothFieldsAndRequiresAcknowledgedRetry() {
        SettingsTestDoubles.OptimisticPreferences remote =
                new SettingsTestDoubles.OptimisticPreferences(NotificationType.FOCUS.bit);
        SettingsStore store = connected(remote);
        remote.queueCommitResults(false, false, false);
        store.setIconColorEnabled(true);
        assertFalse(store.state().iconColorEnabled);
        assertFalse(FilterPreferences.readIconColor(remote.preferences));
        assertState(store, NotificationType.FOCUS.bit, SettingsStore.Status.SAVE_FAILED);
        store.retry();
        assertState(store, NotificationType.FOCUS.bit, SettingsStore.Status.UNAVAILABLE);
        store.retry();
        assertState(store, NotificationType.FOCUS.bit, SettingsStore.Status.READY);
        assertFalse(store.state().iconColorEnabled);
    }

    @Test
    public void initialStateIsWaitingWithEveryFilterOff() {
        SettingsStore store = new SettingsStore(DIRECT, DIRECT);
        assertState(store, 0, SettingsStore.Status.WAITING);
        store.setExcluded(NotificationType.MEDIA, true);
        store.reset();
        assertState(store, 0, SettingsStore.Status.WAITING);
    }

    @Test
    public void togglesPersistAndSurviveANewStoreAndRemoteCache() {
        SettingsTestDoubles.OptimisticPreferences remote =
                new SettingsTestDoubles.OptimisticPreferences();
        SettingsStore store = connected(remote);
        store.setExcluded(NotificationType.FOCUS, true);
        store.setExcluded(NotificationType.MEDIA, true);
        store.setExcluded(NotificationType.FOCUS, false);
        assertState(store, NotificationType.MEDIA.bit, SettingsStore.Status.READY);
        assertEquals(NotificationType.MEDIA.bit, remote.persistedMask());

        SettingsStore reopened = connected(remote.freshConnection());
        assertState(reopened, NotificationType.MEDIA.bit, SettingsStore.Status.READY);
    }

    @Test
    public void resetPersistsAllFiltersOff() {
        SettingsTestDoubles.OptimisticPreferences remote =
                new SettingsTestDoubles.OptimisticPreferences(FilterPreferences.KNOWN_MASK);
        SettingsStore store = connected(remote);
        store.reset();
        assertState(store, 0, SettingsStore.Status.READY);
        assertEquals(0, remote.persistedMask());
        assertEquals(Arrays.asList(0), remote.committedMasks);
    }

    @Test
    public void editsAreIgnoredWhileConnectingOrSaving() {
        SettingsTestDoubles.QueuedExecutor worker = new SettingsTestDoubles.QueuedExecutor();
        SettingsTestDoubles.OptimisticPreferences remote =
                new SettingsTestDoubles.OptimisticPreferences();
        SettingsStore store = new SettingsStore(worker, DIRECT);
        store.connect(new Object(), () -> remote.preferences);
        store.setExcluded(NotificationType.MEDIA, true);
        assertState(store, 0, SettingsStore.Status.CONNECTING);
        worker.runNext();

        store.setExcluded(NotificationType.FOCUS, true);
        store.setExcluded(NotificationType.MEDIA, true);
        store.reset();
        assertState(store, NotificationType.FOCUS.bit, SettingsStore.Status.SAVING);
        assertEquals(1, worker.pending());
        worker.runNext();
        assertState(store, NotificationType.FOCUS.bit, SettingsStore.Status.READY);
        assertEquals(Arrays.asList(NotificationType.FOCUS.bit), remote.committedMasks);
    }

    @Test
    public void anOldLoadSuccessDeliveredLastCannotOverwriteTheNewConnection() {
        SettingsTestDoubles.QueuedExecutor worker = new SettingsTestDoubles.QueuedExecutor();
        SettingsTestDoubles.QueuedExecutor main = new SettingsTestDoubles.QueuedExecutor();
        SettingsStore store = new SettingsStore(worker, main);
        SettingsTestDoubles.OptimisticPreferences oldRemote =
                new SettingsTestDoubles.OptimisticPreferences(NotificationType.MEDIA.bit);
        SettingsTestDoubles.OptimisticPreferences newRemote =
                new SettingsTestDoubles.OptimisticPreferences(NotificationType.CALL.bit);
        store.connect(new Object(), () -> oldRemote.preferences);
        worker.runNext();
        store.connect(new Object(), () -> newRemote.preferences);
        worker.runNext();
        main.runLast();
        assertState(store, NotificationType.CALL.bit, SettingsStore.Status.READY);
        main.runNext();
        assertState(store, NotificationType.CALL.bit, SettingsStore.Status.READY);
    }

    @Test
    public void anOldLoadFailureDeliveredLastCannotDisableTheNewConnection() {
        SettingsTestDoubles.QueuedExecutor worker = new SettingsTestDoubles.QueuedExecutor();
        SettingsTestDoubles.QueuedExecutor main = new SettingsTestDoubles.QueuedExecutor();
        SettingsStore store = new SettingsStore(worker, main);
        store.connect(new Object(), () -> { throw new IllegalStateException("Old service died"); });
        worker.runNext();
        SettingsTestDoubles.OptimisticPreferences remote =
                new SettingsTestDoubles.OptimisticPreferences(NotificationType.FOLDED.bit);
        store.connect(new Object(), () -> remote.preferences);
        worker.runNext();
        main.runLast();
        main.runNext();
        assertState(store, NotificationType.FOLDED.bit, SettingsStore.Status.READY);
    }

    @Test
    public void anOldSaveAcknowledgementCannotReplaceTheNewConnectionState() {
        SettingsTestDoubles.QueuedExecutor worker = new SettingsTestDoubles.QueuedExecutor();
        SettingsTestDoubles.QueuedExecutor main = new SettingsTestDoubles.QueuedExecutor();
        SettingsStore store = new SettingsStore(worker, main);
        SettingsTestDoubles.OptimisticPreferences oldRemote =
                new SettingsTestDoubles.OptimisticPreferences();
        store.connect(new Object(), () -> oldRemote.preferences);
        worker.runNext();
        main.runNext();
        store.setExcluded(NotificationType.MEDIA, true);
        worker.runNext();

        SettingsTestDoubles.OptimisticPreferences newRemote =
                new SettingsTestDoubles.OptimisticPreferences(NotificationType.NO_CLEAR.bit);
        store.connect(new Object(), () -> newRemote.preferences);
        worker.runNext();
        main.runLast();
        main.runNext();
        assertState(store, NotificationType.NO_CLEAR.bit, SettingsStore.Status.READY);
    }

    @Test
    public void anOldServiceDeathDoesNotDisconnectTheCurrentService() {
        SettingsStore store = new SettingsStore(DIRECT, DIRECT);
        Object oldService = new Object();
        Object currentService = new Object();
        SettingsTestDoubles.OptimisticPreferences remote =
                new SettingsTestDoubles.OptimisticPreferences(NotificationType.CALL.bit);
        store.connect(oldService, () -> remote.preferences);
        store.connect(currentService, () -> remote.preferences);
        store.disconnect(oldService);
        assertState(store, NotificationType.CALL.bit, SettingsStore.Status.READY);
        store.disconnect(currentService);
        assertState(store, NotificationType.CALL.bit, SettingsStore.Status.UNAVAILABLE);
    }

    @Test
    public void failedSaveRestoresTheAcknowledgedMaskAndRejectsFurtherEdits() {
        int previous = NotificationType.FOCUS.bit;
        SettingsTestDoubles.OptimisticPreferences remote =
                new SettingsTestDoubles.OptimisticPreferences(previous);
        remote.queueCommitResults(false, false);
        SettingsStore store = connected(remote);
        store.setExcluded(NotificationType.MEDIA, true);

        assertState(store, previous, SettingsStore.Status.SAVE_FAILED);
        assertEquals(previous, remote.cachedMask());
        assertEquals(previous, remote.persistedMask());
        assertEquals(Arrays.asList(previous | NotificationType.MEDIA.bit, previous),
                remote.committedMasks);
        store.setExcluded(NotificationType.CALL, true);
        store.reset();
        assertEquals(2, remote.committedMasks.size());
        assertState(store, previous, SettingsStore.Status.SAVE_FAILED);
    }

    @Test
    public void retryCannotBecomeReadyUntilThePreviousMaskIsAcknowledged() {
        int previous = NotificationType.FOCUS.bit;
        SettingsTestDoubles.QueuedExecutor worker = new SettingsTestDoubles.QueuedExecutor();
        SettingsTestDoubles.OptimisticPreferences remote =
                new SettingsTestDoubles.OptimisticPreferences(previous);
        remote.queueCommitResults(false, false, false);
        SettingsStore store = new SettingsStore(worker, DIRECT);
        store.connect(new Object(), () -> remote.preferences);
        worker.runNext();
        store.setExcluded(NotificationType.MEDIA, true);
        worker.runNext();
        assertState(store, previous, SettingsStore.Status.SAVE_FAILED);

        store.retry();
        assertState(store, previous, SettingsStore.Status.CONNECTING);
        worker.runNext();
        assertState(store, previous, SettingsStore.Status.UNAVAILABLE);
        assertEquals(1, remote.intReads);

        store.retry();
        worker.runNext();
        assertState(store, previous, SettingsStore.Status.READY);
        assertEquals(2, remote.intReads);
        assertEquals(Arrays.asList(previous | NotificationType.MEDIA.bit,
                previous, previous, previous), remote.committedMasks);
    }

    @Test
    public void duplicateBindsDoNotInterruptConnectingReadyOrSaving() {
        SettingsTestDoubles.QueuedExecutor worker = new SettingsTestDoubles.QueuedExecutor();
        SettingsStore store = new SettingsStore(worker, DIRECT);
        Object service = new Object();
        SettingsTestDoubles.OptimisticPreferences remote =
                new SettingsTestDoubles.OptimisticPreferences();
        AtomicInteger unexpectedReads = new AtomicInteger();
        java.util.function.Supplier<SharedPreferences> duplicate = () -> {
            unexpectedReads.incrementAndGet();
            throw new IllegalStateException("Duplicate bind must not load");
        };
        store.connect(service, () -> remote.preferences);
        store.connect(service, duplicate);
        assertEquals(1, worker.pending());
        worker.runNext();
        store.connect(service, duplicate);
        assertState(store, 0, SettingsStore.Status.READY);

        store.setExcluded(NotificationType.MEDIA, true);
        store.connect(service, duplicate);
        assertState(store, NotificationType.MEDIA.bit, SettingsStore.Status.SAVING);
        assertEquals(1, worker.pending());
        worker.runNext();
        assertState(store, NotificationType.MEDIA.bit, SettingsStore.Status.READY);
        assertEquals(0, unexpectedReads.get());
    }

    @Test
    public void sameServiceRebindAfterSaveFailureStillConfirmsThePreviousMask() {
        int previous = NotificationType.FOCUS.bit;
        Object service = new Object();
        SettingsTestDoubles.OptimisticPreferences remote =
                new SettingsTestDoubles.OptimisticPreferences(previous);
        remote.queueCommitResults(false, false, false);
        SettingsStore store = new SettingsStore(DIRECT, DIRECT);
        store.connect(service, () -> remote.preferences);
        store.setExcluded(NotificationType.MEDIA, true);
        store.connect(service, () -> remote.preferences);
        assertState(store, previous, SettingsStore.Status.UNAVAILABLE);
        assertEquals(1, remote.intReads);

        store.connect(service, () -> remote.preferences);
        assertState(store, previous, SettingsStore.Status.READY);
        assertEquals(Arrays.asList(previous | NotificationType.MEDIA.bit,
                previous, previous, previous), remote.committedMasks);
    }

    @Test
    public void aFreshServiceUsesItsStoredMaskRatherThanRollingBackTheOldService() {
        SettingsTestDoubles.OptimisticPreferences oldRemote =
                new SettingsTestDoubles.OptimisticPreferences(NotificationType.FOCUS.bit);
        oldRemote.queueCommitResults(false, false);
        SettingsStore store = connected(oldRemote);
        store.setExcluded(NotificationType.MEDIA, true);
        SettingsTestDoubles.OptimisticPreferences newRemote =
                new SettingsTestDoubles.OptimisticPreferences(NotificationType.CALL.bit);
        store.connect(new Object(), () -> newRemote.preferences);
        assertState(store, NotificationType.CALL.bit, SettingsStore.Status.READY);
        assertEquals(0, newRemote.committedMasks.size());
    }

    private static SettingsStore connected(SettingsTestDoubles.OptimisticPreferences remote) {
        SettingsStore store = new SettingsStore(DIRECT, DIRECT);
        store.connect(new Object(), () -> remote.preferences);
        return store;
    }

    private static void assertState(SettingsStore store, int mask, SettingsStore.Status status) {
        assertEquals(mask, store.state().mask);
        assertEquals(status, store.state().status);
    }
}
