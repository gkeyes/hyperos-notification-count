package dev.hyperos.notificationcount.hook;

import android.app.Notification;
import android.os.Bundle;

import org.json.JSONException;
import org.json.JSONObject;

import dev.hyperos.notificationcount.core.NotificationType;

/** Public Android properties and explicit island payload presence, not display-state guesses. */
final class NotificationPayloadTypes {
    private static final String[] FOCUS_KEYS = {
            "miui.focus.param", "miui.focus.param.custom", "miui.focus.param.media"
    };

    private NotificationPayloadTypes() { }

    static int publicTypes(Notification notification, int requested) {
        int types = 0;
        if ((notification.flags & Notification.FLAG_ONGOING_EVENT) != 0) types |= NotificationType.ONGOING_EVENT.bit;
        if ((notification.flags & Notification.FLAG_NO_CLEAR) != 0) types |= NotificationType.NO_CLEAR.bit;
        if ((notification.flags & Notification.FLAG_FOREGROUND_SERVICE) != 0) types |= NotificationType.FOREGROUND_SERVICE.bit;
        if ((requested & NotificationType.REQUEST_PROMOTION.bit) != 0
                && notification.isRequestPromotedOngoing()) types |= NotificationType.REQUEST_PROMOTION.bit;
        if ((requested & NotificationType.CALL.bit) != 0 && isCall(notification)) types |= NotificationType.CALL.bit;
        if ((requested & NotificationType.ISLAND_CONTENT.bit) != 0 && hasIslandContent(notification.extras)) {
            types |= NotificationType.ISLAND_CONTENT.bit;
        }
        return types & requested;
    }

    static boolean hasIslandContent(Bundle extras) {
        if (extras == null) return false;
        for (String key : FOCUS_KEYS) {
            Object raw = extras.get(key);
            if (!(raw instanceof String text) || text.isEmpty()) continue;
            try {
                JSONObject root = new JSONObject(text);
                JSONObject v2 = root.optJSONObject("param_v2");
                if (v2 != null && nonempty(v2.optJSONObject("param_island"))) return true;
                if (nonempty(root.optJSONObject("param_island"))) return true;
            } catch (JSONException ignored) {
                // An invalid or empty payload does not make the notification an island entry.
            }
        }
        return false;
    }

    private static boolean nonempty(JSONObject object) { return object != null && object.length() > 0; }

    private static boolean isCall(Notification notification) {
        return Notification.CATEGORY_CALL.equals(notification.category)
                || (notification.extras != null && Notification.CallStyle.class.getName().equals(
                        notification.extras.getString(Notification.EXTRA_TEMPLATE)));
    }
}
