package dev.hyperos.notificationcount.core;

/** Stable keys and persisted filter bits; labels belong to the settings resources. */
public enum NotificationType {
    FOCUS("focus", 1 << 0),
    ISLAND_CONTENT("island_content", 1 << 1),
    UPDATABLE_FOCUS("updatable_focus", 1 << 2),
    PROMOTED_ONGOING("promoted_ongoing", 1 << 3),
    REQUEST_PROMOTION("request_promotion", 1 << 4),
    PERSISTENT("persistent", 1 << 5),
    ONGOING_EVENT("ongoing_event", 1 << 6),
    NO_CLEAR("no_clear", 1 << 7),
    FOREGROUND_SERVICE("foreground_service", 1 << 8),
    NOT_CLEARABLE("not_clearable", 1 << 9),
    HEADS_UP_PINNED("heads_up_pinned", 1 << 10),
    MEDIA("media", 1 << 11),
    CALL("call", 1 << 12),
    SILENT("silent", 1 << 13),
    FOLDED("folded", 1 << 14);

    public final String key;
    public final int bit;

    NotificationType(String key, int bit) {
        this.key = key;
        this.bit = bit;
    }
}
