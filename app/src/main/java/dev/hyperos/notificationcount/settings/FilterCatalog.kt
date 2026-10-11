package dev.hyperos.notificationcount.settings

import dev.hyperos.notificationcount.R
import androidx.compose.ui.graphics.vector.ImageVector
import dev.hyperos.notificationcount.core.NotificationType
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Blocklist
import top.yukonga.miuix.kmp.icon.extended.Clear
import top.yukonga.miuix.kmp.icon.extended.FavoritesFill
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.Layers
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.icon.extended.Music
import top.yukonga.miuix.kmp.icon.extended.Phone
import top.yukonga.miuix.kmp.icon.extended.Pin
import top.yukonga.miuix.kmp.icon.extended.Promotions
import top.yukonga.miuix.kmp.icon.extended.Send
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Timer
import top.yukonga.miuix.kmp.icon.extended.Update
import top.yukonga.miuix.kmp.icon.extended.VolumeOff

/** UI grouping of the 15 filters. Keys and bits live in [NotificationType]; this is display-only. */
internal data class FilterGroup(val title: Int, val types: List<NotificationType>)

internal val filterGroups = listOf(
    FilterGroup(R.string.settings_section_focus, listOf(
        NotificationType.FOCUS, NotificationType.ISLAND_CONTENT,
        NotificationType.UPDATABLE_FOCUS, NotificationType.PROMOTED_ONGOING,
        NotificationType.REQUEST_PROMOTION,
    )),
    FilterGroup(R.string.settings_section_ongoing, listOf(
        NotificationType.PERSISTENT, NotificationType.ONGOING_EVENT,
        NotificationType.NO_CLEAR, NotificationType.FOREGROUND_SERVICE,
        NotificationType.NOT_CLEARABLE,
    )),
    FilterGroup(R.string.settings_section_content, listOf(
        NotificationType.HEADS_UP_PINNED, NotificationType.MEDIA, NotificationType.CALL,
        NotificationType.SILENT, NotificationType.FOLDED,
    )),
)

internal fun FilterGroup.enabledCount(mask: Int): Int = types.count { mask and it.bit != 0 }

internal fun filterLabels(type: NotificationType): Pair<Int, Int> = when (type) {
    NotificationType.FOCUS -> R.string.filter_focus_title to R.string.filter_focus_description
    NotificationType.ISLAND_CONTENT -> R.string.filter_island_title to R.string.filter_island_description
    NotificationType.UPDATABLE_FOCUS -> R.string.filter_updatable_title to R.string.filter_updatable_description
    NotificationType.PROMOTED_ONGOING -> R.string.filter_promoted_title to R.string.filter_promoted_description
    NotificationType.REQUEST_PROMOTION -> R.string.filter_request_title to R.string.filter_request_description
    NotificationType.PERSISTENT -> R.string.filter_persistent_title to R.string.filter_persistent_description
    NotificationType.ONGOING_EVENT -> R.string.filter_ongoing_title to R.string.filter_ongoing_description
    NotificationType.NO_CLEAR -> R.string.filter_no_clear_title to R.string.filter_no_clear_description
    NotificationType.FOREGROUND_SERVICE -> R.string.filter_foreground_title to R.string.filter_foreground_description
    NotificationType.NOT_CLEARABLE -> R.string.filter_not_clearable_title to R.string.filter_not_clearable_description
    NotificationType.HEADS_UP_PINNED -> R.string.filter_pinned_title to R.string.filter_pinned_description
    NotificationType.MEDIA -> R.string.filter_media_title to R.string.filter_media_description
    NotificationType.CALL -> R.string.filter_call_title to R.string.filter_call_description
    NotificationType.SILENT -> R.string.filter_silent_title to R.string.filter_silent_description
    NotificationType.FOLDED -> R.string.filter_folded_title to R.string.filter_folded_description
}

/** Leading icon of each filter row; purely decorative. */
internal fun filterIcon(type: NotificationType): ImageVector = when (type) {
    NotificationType.FOCUS -> MiuixIcons.FavoritesFill
    NotificationType.ISLAND_CONTENT -> MiuixIcons.Layers
    NotificationType.UPDATABLE_FOCUS -> MiuixIcons.Update
    NotificationType.PROMOTED_ONGOING -> MiuixIcons.Promotions
    NotificationType.REQUEST_PROMOTION -> MiuixIcons.Send
    NotificationType.PERSISTENT -> MiuixIcons.Lock
    NotificationType.ONGOING_EVENT -> MiuixIcons.Timer
    NotificationType.NO_CLEAR -> MiuixIcons.Blocklist
    NotificationType.FOREGROUND_SERVICE -> MiuixIcons.Settings
    NotificationType.NOT_CLEARABLE -> MiuixIcons.Clear
    NotificationType.HEADS_UP_PINNED -> MiuixIcons.Pin
    NotificationType.MEDIA -> MiuixIcons.Music
    NotificationType.CALL -> MiuixIcons.Phone
    NotificationType.SILENT -> MiuixIcons.VolumeOff
    NotificationType.FOLDED -> MiuixIcons.Folder
}
