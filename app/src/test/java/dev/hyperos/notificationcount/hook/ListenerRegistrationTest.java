package dev.hyperos.notificationcount.hook;

import org.junit.Test;
import static org.junit.Assert.*;

public class ListenerRegistrationTest {
    @Test public void partialFailureRetriesMissingListenerWithoutAddingCollectionAgain() throws Throwable {
        ListenerRegistration registration = new ListenerRegistration();
        int[] calls = new int[2];
        IllegalStateException failure = new IllegalStateException("render list is currently building");
        try {
            registration.ensure(() -> calls[0]++, () -> { calls[1]++; throw failure; });
            fail();
        } catch (IllegalStateException actual) { assertSame(failure, actual); }
        registration.ensure(() -> calls[0]++, () -> calls[1]++);
        assertArrayEquals(new int[]{1, 2}, calls);
    }

    @Test public void firstFailureLeavesBothRegistrationsRetryable() throws Throwable {
        ListenerRegistration registration = new ListenerRegistration();
        int[] calls = new int[2];
        try {
            registration.ensure(() -> { calls[0]++; throw new IllegalStateException(); }, () -> calls[1]++);
            fail();
        } catch (IllegalStateException expected) { }
        registration.ensure(() -> calls[0]++, () -> calls[1]++);
        assertArrayEquals(new int[]{2, 1}, calls);
    }

    @Test public void completedRegistrationIsIdempotent() throws Throwable {
        ListenerRegistration registration = new ListenerRegistration();
        int[] calls = new int[2];
        registration.ensure(() -> calls[0]++, () -> calls[1]++);
        registration.ensure(() -> calls[0]++, () -> calls[1]++);
        assertArrayEquals(new int[]{1, 1}, calls);
    }
}
