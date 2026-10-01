package dev.hyperos.notificationcount.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import dev.hyperos.notificationcount.BuildConfig
import dev.hyperos.notificationcount.R
import dev.hyperos.notificationcount.core.NotificationType
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

private data class FilterGroup(val title: Int, val types: List<NotificationType>)

private val filterGroups = listOf(
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

@Composable
internal fun SettingsScreen(
    state: SettingsStore.State,
    onFilterChange: (NotificationType, Boolean) -> Unit,
    onReset: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    val editable = state.status == SettingsStore.Status.READY
    val scrollBehavior = MiuixScrollBehavior()
    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(R.string.settings_title),
                subtitle = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                navigationIcon = {
                    IconButton(onClick = onBack, minWidth = 48.dp, minHeight = 48.dp) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = stringResource(R.string.settings_back),
                            tint = MiuixTheme.colorScheme.onSurface,
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .consumeWindowInsets(insets)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState())
                .testTag("settings-scroll")
                .padding(top = 12.dp, bottom = 24.dp),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                insideMargin = PaddingValues(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.settings_intro),
                    style = MiuixTheme.textStyles.body1,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.settings_overlap),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            Spacer(Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                insideMargin = PaddingValues(16.dp),
            ) {
                Text(
                    text = stringResource(statusLabel(state.status)),
                    modifier = Modifier.testTag("settings-status").semantics {
                        liveRegion = LiveRegionMode.Polite
                    },
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.settings_selected, Integer.bitCount(state.mask)),
                    modifier = Modifier.testTag("settings-selected"),
                    style = MiuixTheme.textStyles.body1,
                )
            }
            filterGroups.forEach { group ->
                Spacer(Modifier.height(16.dp))
                SmallTitle(text = stringResource(group.title))
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    group.types.forEach { type ->
                        val (titleId, descriptionId) = filterLabels(type)
                        val title = stringResource(titleId)
                        val description = stringResource(descriptionId)
                        val checked = state.mask and type.bit != 0
                        SwitchPreference(
                            title = title,
                            summary = description,
                            checked = checked,
                            enabled = editable,
                            onCheckedChange = { onFilterChange(type, it) },
                            modifier = Modifier.testTag("filter-${type.key}")
                                .clearAndSetSemantics {
                                    // The library handles row/toggle touch input. Expose one
                                    // accessible switch instead of two separate click targets.
                                    contentDescription = "$title。$description"
                                    role = Role.Switch
                                    toggleableState = if (checked) ToggleableState.On else ToggleableState.Off
                                    if (editable) {
                                        onClick {
                                            onFilterChange(type, !checked)
                                            true
                                        }
                                    } else {
                                        disabled()
                                    }
                                },
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            TextButton(
                text = stringResource(R.string.settings_reset),
                onClick = onReset,
                enabled = editable && state.mask != 0,
                minHeight = 48.dp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                    .testTag("settings-reset"),
            )
            if (state.status != SettingsStore.Status.READY && state.status != SettingsStore.Status.SAVING) {
                Spacer(Modifier.height(8.dp))
                TextButton(
                    text = stringResource(R.string.settings_retry),
                    onClick = onRetry,
                    enabled = state.status != SettingsStore.Status.CONNECTING,
                    minHeight = 48.dp,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                        .testTag("settings-retry"),
                )
            }
        }
    }
}

private fun statusLabel(status: SettingsStore.Status): Int = when (status) {
    SettingsStore.Status.WAITING -> R.string.settings_waiting
    SettingsStore.Status.CONNECTING -> R.string.settings_connecting
    SettingsStore.Status.READY -> R.string.settings_ready
    SettingsStore.Status.SAVING -> R.string.settings_saving
    SettingsStore.Status.UNAVAILABLE -> R.string.settings_unavailable
    SettingsStore.Status.SAVE_FAILED -> R.string.settings_save_failed
}

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
