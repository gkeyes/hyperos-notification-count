package dev.hyperos.notificationcount.core;

import java.util.Objects;

/** Package resources are isolated by Android user, including app clones. */
public final class AppIconSource {
    public final String packageName;
    public final int userId;

    public AppIconSource(String packageName, int userId) {
        this.packageName = Objects.requireNonNull(packageName);
        this.userId = userId;
    }

    @Override public boolean equals(Object other) {
        return other instanceof AppIconSource source
                && userId == source.userId && packageName.equals(source.packageName);
    }

    @Override public int hashCode() { return 31 * packageName.hashCode() + userId; }
}
