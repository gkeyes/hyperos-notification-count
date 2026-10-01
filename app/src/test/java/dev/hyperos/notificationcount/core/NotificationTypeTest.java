package dev.hyperos.notificationcount.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

public class NotificationTypeTest {
    @Test
    public void keysAndBitsKeepThePersistedSettingsContract() {
        assertType(NotificationType.FOCUS, "focus", 0x0001);
        assertType(NotificationType.ISLAND_CONTENT, "island_content", 0x0002);
        assertType(NotificationType.UPDATABLE_FOCUS, "updatable_focus", 0x0004);
        assertType(NotificationType.PROMOTED_ONGOING, "promoted_ongoing", 0x0008);
        assertType(NotificationType.REQUEST_PROMOTION, "request_promotion", 0x0010);
        assertType(NotificationType.PERSISTENT, "persistent", 0x0020);
        assertType(NotificationType.ONGOING_EVENT, "ongoing_event", 0x0040);
        assertType(NotificationType.NO_CLEAR, "no_clear", 0x0080);
        assertType(NotificationType.FOREGROUND_SERVICE, "foreground_service", 0x0100);
        assertType(NotificationType.NOT_CLEARABLE, "not_clearable", 0x0200);
        assertType(NotificationType.HEADS_UP_PINNED, "heads_up_pinned", 0x0400);
        assertType(NotificationType.MEDIA, "media", 0x0800);
        assertType(NotificationType.CALL, "call", 0x1000);
        assertType(NotificationType.SILENT, "silent", 0x2000);
        assertType(NotificationType.FOLDED, "folded", 0x4000);
    }

    @Test
    public void everyTypeHasAUniqueKeyAndSingleBit() {
        Set<String> keys = new HashSet<>();
        int combined = 0;
        for (NotificationType type : NotificationType.values()) {
            assertTrue("Duplicate key: " + type.key, keys.add(type.key));
            assertEquals(type.key, 1, Integer.bitCount(type.bit));
            assertEquals("Duplicate bit: " + type.key, 0, combined & type.bit);
            combined |= type.bit;
        }
    }

    private static void assertType(NotificationType type, String key, int bit) {
        assertEquals(key, type.key);
        assertEquals(key, bit, type.bit);
    }
}
