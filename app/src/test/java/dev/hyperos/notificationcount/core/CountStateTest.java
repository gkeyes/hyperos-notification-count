package dev.hyperos.notificationcount.core;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class CountStateTest {
    @Test
    public void zeroHasItsOwnDisplayState() {
        assertEquals(0, CountState.fromCount(0));
    }

    @Test
    public void oneThroughNineKeepTheirExactDigits() {
        for (int count = 1; count <= 9; count++) {
            assertEquals(count, CountState.fromCount(count));
        }
    }

    @Test
    public void countsAboveNineUseTheOverflowState() {
        assertEquals(10, CountState.fromCount(10));
        assertEquals(10, CountState.fromCount(11));
        assertEquals(10, CountState.fromCount(Integer.MAX_VALUE));
    }

    @Test
    public void negativeInputsUseTheEmptyState() {
        assertEquals(0, CountState.fromCount(-1));
    }
}
