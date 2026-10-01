package dev.hyperos.notificationcount.hook;

import android.app.Notification;
import android.os.Bundle;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import dev.hyperos.notificationcount.core.NotificationType;
import dev.hyperos.notificationcount.settings.FilterPreferences;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 37)
public class NotificationPayloadTypesAndroidTest {
    @Test public void disabledFiltersIgnoreEvenMatchingPayloads() {
        Notification notification = new Notification();
        notification.flags = Notification.FLAG_ONGOING_EVENT | Notification.FLAG_FOREGROUND_SERVICE;
        notification.extras.putString("miui.focus.param", "{\"param_v2\":{\"param_island\":{\"bigIslandArea\":{}}}}");
        assertEquals(0, NotificationPayloadTypes.publicTypes(notification, 0));
    }

    @Test public void threePersistentFlagsStayIndependent() {
        Notification notification = new Notification();
        notification.flags = Notification.FLAG_ONGOING_EVENT | Notification.FLAG_NO_CLEAR;
        int result = NotificationPayloadTypes.publicTypes(notification, FilterPreferences.KNOWN_MASK);
        assertEquals(NotificationType.ONGOING_EVENT.bit | NotificationType.NO_CLEAR.bit, result);
        assertEquals(NotificationType.NO_CLEAR.bit,
                NotificationPayloadTypes.publicTypes(notification, NotificationType.NO_CLEAR.bit));
        notification.flags = Notification.FLAG_FOREGROUND_SERVICE;
        assertEquals(NotificationType.FOREGROUND_SERVICE.bit,
                NotificationPayloadTypes.publicTypes(notification, FilterPreferences.KNOWN_MASK));
    }

    @Test public void allThreeIslandExtrasRecognizeNestedAndLegacyObjects() {
        for (String key : new String[]{"miui.focus.param", "miui.focus.param.custom", "miui.focus.param.media"}) {
            Bundle extras = new Bundle();
            extras.putString(key, "{\"param_v2\":{\"param_island\":{\"smallIslandArea\":{}}}}");
            assertTrue(key, NotificationPayloadTypes.hasIslandContent(extras));
            extras.putString(key, "{\"param_island\":{\"bigIslandArea\":{}}}");
            assertTrue(key, NotificationPayloadTypes.hasIslandContent(extras));
        }
    }

    @Test public void malformedEmptyAndNonObjectIslandPayloadsDoNotMatch() {
        assertFalse(NotificationPayloadTypes.hasIslandContent(null));
        for (String value : new String[]{"", "not json", "[]", "{}", "{\"param_island\":{}}",
                "{\"param_v2\":{\"param_island\":[]}}", "{\"param_island\":true}",
                "{\"param_v2\":{\"updatable\":true}}"}) {
            Bundle extras = new Bundle();
            extras.putString("miui.focus.param", value);
            assertFalse(value, NotificationPayloadTypes.hasIslandContent(extras));
        }
        Bundle extras = new Bundle();
        extras.putInt("miui.focus.param", 1);
        assertFalse(NotificationPayloadTypes.hasIslandContent(extras));
    }

    @Test public void invalidMainPayloadDoesNotHideValidCustomPayload() {
        Bundle extras = new Bundle();
        extras.putString("miui.focus.param", "invalid");
        extras.putString("miui.focus.param.custom", "{\"param_v2\":{\"param_island\":{\"shareData\":{}}}}");
        assertTrue(NotificationPayloadTypes.hasIslandContent(extras));
    }

    @Test public void promotionRequestIsSeparateFromGrantedFlag() {
        Notification notification = new Notification();
        notification.flags = Notification.FLAG_PROMOTED_ONGOING;
        assertEquals(0, NotificationPayloadTypes.publicTypes(notification, NotificationType.REQUEST_PROMOTION.bit));
        notification.extras.putBoolean("android.requestPromotedOngoing", true);
        assertEquals(NotificationType.REQUEST_PROMOTION.bit,
                NotificationPayloadTypes.publicTypes(notification, NotificationType.REQUEST_PROMOTION.bit));
    }

    @Test public void callCategoryAndCallStyleMatchWithoutPromotingOtherCategories() {
        Notification notification = new Notification();
        notification.category = Notification.CATEGORY_CALL;
        assertEquals(NotificationType.CALL.bit,
                NotificationPayloadTypes.publicTypes(notification, NotificationType.CALL.bit));
        notification.category = Notification.CATEGORY_MESSAGE;
        assertEquals(0, NotificationPayloadTypes.publicTypes(notification, NotificationType.CALL.bit));
        notification.extras.putString(Notification.EXTRA_TEMPLATE, Notification.CallStyle.class.getName());
        assertEquals(NotificationType.CALL.bit,
                NotificationPayloadTypes.publicTypes(notification, NotificationType.CALL.bit));
    }
}
