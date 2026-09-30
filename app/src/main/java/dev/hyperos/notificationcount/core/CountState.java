package dev.hyperos.notificationcount.core;

/** Maps counts to the available display states: zero, one through nine, or overflow. */
public final class CountState {
    private CountState() {
    }

    public static int fromCount(int count) {
        if (count <= 0) {
            return 0;
        }
        return Math.min(count, 10);
    }
}
