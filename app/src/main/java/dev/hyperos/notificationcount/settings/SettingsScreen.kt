package dev.hyperos.notificationcount.settings

import androidx.compose.animation.AnimatedContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import top.yukonga.miuix.kmp.icon.extended.Filter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import dev.hyperos.notificationcount.BuildConfig
import dev.hyperos.notificationcount.R
import dev.hyperos.notificationcount.core.NotificationType
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Recent
import top.yukonga.miuix.kmp.icon.extended.Report
import top.yukonga.miuix.kmp.icon.extended.Show
import top.yukonga.miuix.kmp.icon.extended.Stopwatch
import top.yukonga.miuix.kmp.icon.extended.Theme
import top.yukonga.miuix.kmp.icon.extended.Tune

/** Home is the root; the two sub-pages hold the long lists that used to share one scroll. */
internal enum class SettingsPage(val screen: String) {
    HOME("settings"),
    DISPLAY("settings-display"),
    FILTERS("settings-filters"),
}

/**
 * Three pages: home (live preview, connection state and links), display style, and the
 * three filter groups. System Back and the top-bar arrow return a sub-page to home; the
 * page survives recreation.
 */
@Composable
internal fun SettingsScreen(
    state: SettingsStore.State,
    onFilterChange: (NotificationType, Boolean) -> Unit,
    onIconColorChange: (Boolean) -> Unit,
    onTemporaryColorChange: (Boolean) -> Unit,
    onColorDurationChange: (Int) -> Unit,
    onDigitColorChange: (Int) -> Unit,
    onBadgeContrastChange: (Int) -> Unit,
    onDigitWeightChange: (Int) -> Unit,
    onDigitContrastChange: (Int) -> Unit,
    onReset: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    var page by rememberSaveable { mutableStateOf(SettingsPage.HOME) }
    val goHome = { page = SettingsPage.HOME }
    BackHandler(enabled = page != SettingsPage.HOME, onBack = goHome)
    val reduce = reduceMotion
    AnimatedContent(
        targetState = page,
        transitionSpec = { Motion.pagePush(forward = targetState != SettingsPage.HOME, reduce = reduce) },
        label = "settingsPage",
    ) { current ->
        when (current) {
            SettingsPage.HOME -> PageScaffold(
                title = stringResource(R.string.settings_title),
                subtitle = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                onBack = onBack,
            ) {
                var block = 0
                Preview(state, Modifier.staggeredEntrance(current.screen, block++))
                Spacer(Modifier.height(Spacing.sm))
                ConnectionCard(
                    status = state.status,
                    enabledFilters = Integer.bitCount(state.mask),
                    onRetry = onRetry,
                    modifier = Modifier.staggeredEntrance(current.screen, block++),
                )
                Spacer(Modifier.height(Spacing.sm))
                Footnote(
                    text = stringResource(R.string.settings_intro),
                    modifier = Modifier.staggeredEntrance(current.screen, block++),
                )
                Spacer(Modifier.height(Spacing.sm))
                GroupCard(Modifier.staggeredEntrance(current.screen, block++)) {
                    NavRow(
                        icon = MiuixIcons.Theme,
                        title = stringResource(R.string.settings_section_display),
                        summary = stringResource(R.string.settings_nav_display_summary),
                        onClick = { page = SettingsPage.DISPLAY },
                        testTag = "nav-display",
                    )
                    NavRow(
                        icon = MiuixIcons.Filter,
                        title = stringResource(R.string.settings_section_filters),
                        summary = stringResource(R.string.settings_selected, Integer.bitCount(state.mask)),
                        onClick = { page = SettingsPage.FILTERS },
                        testTag = "nav-filters",
                    )
                }
            }

            SettingsPage.DISPLAY -> PageScaffold(
                title = stringResource(R.string.settings_section_display),
                onBack = goHome,
            ) {
                var block = 0
                Preview(state, Modifier.staggeredEntrance(current.screen, block++))
                UnsettledConnection(state, onRetry, Modifier.staggeredEntrance(current.screen, block++))
                Spacer(Modifier.height(Spacing.sm))
                Column(Modifier.staggeredEntrance(current.screen, block++)) {
                    DisplaySection(
                        state, state.status == SettingsStore.Status.READY,
                        onIconColorChange, onTemporaryColorChange, onColorDurationChange,
                        onDigitColorChange, onBadgeContrastChange, onDigitWeightChange, onDigitContrastChange,
                    )
                }
            }

            SettingsPage.FILTERS -> PageScaffold(
                title = stringResource(R.string.settings_section_filters),
                onBack = goHome,
            ) {
                val editable = state.status == SettingsStore.Status.READY
                var block = 0
                // Always shown here: the banner carries the "N filters on" count for this page.
                ConnectionCard(
                    status = state.status,
                    enabledFilters = Integer.bitCount(state.mask),
                    onRetry = onRetry,
                    modifier = Modifier.staggeredEntrance(current.screen, block++),
                )
                Spacer(Modifier.height(Spacing.sm))
                Footnote(
                    text = stringResource(R.string.settings_overlap),
                    modifier = Modifier.staggeredEntrance(current.screen, block++),
                )
                filterGroups.forEach { group ->
                    Column(Modifier.staggeredEntrance(current.screen, block++)) {
                        SectionHeader(
                            title = stringResource(group.title),
                            enabled = group.enabledCount(state.mask),
                            total = group.types.size,
                        )
                        FilterSection(group, state.mask, editable, onFilterChange)
                    }
                }
                Spacer(Modifier.height(Spacing.lg))
                FamilyButton(
                    text = stringResource(R.string.settings_reset),
                    onClick = onReset,
                    enabled = editable && state.mask != 0,
                    testTag = "settings-reset",
                    kind = ButtonKind.DESTRUCTIVE,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter),
                )
            }
        }
    }
}

/**
 * One page: Miuix large title that collapses on scroll with a hairline fading in; content is
 * edge-to-edge and scrolls behind the navigation bar.
 */
@Composable
private fun PageScaffold(
    title: String,
    onBack: () -> Unit,
    subtitle: String = "",
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val layoutDirection = LocalLayoutDirection.current
    val family = Family.colors
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = title,
                    subtitle = subtitle,
                    color = family.page,
                    navigationIcon = {
                        IconButton(
                            onClick = onBack,
                            minWidth = Dimens.minTouch,
                            minHeight = Dimens.minTouch,
                            modifier = Modifier.testTag("settings-back"),
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Back,
                                contentDescription = stringResource(R.string.settings_back),
                                tint = textPrimary,
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
                // Hairline appears as the large title collapses (read in the draw phase).
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(Dimens.hairline)
                        .graphicsLayer { alpha = scrollBehavior.state.collapsedFraction }
                        .background(family.hairline),
                )
            }
        },
    ) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = insets.calculateStartPadding(layoutDirection),
                    top = insets.calculateTopPadding(),
                    end = insets.calculateEndPadding(layoutDirection),
                )
                .consumeWindowInsets(insets)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState())
                .testTag("settings-scroll")
                .padding(top = Spacing.sm),
        ) {
            content()
            Spacer(Modifier.height(Spacing.lg + insets.calculateBottomPadding()))
        }
    }
}

@Composable
private fun Preview(state: SettingsStore.State, modifier: Modifier) {
    CountBadgePreview(
        settings = BadgePreviewSettings(
            iconColorEnabled = state.iconColorEnabled,
            temporaryColor = state.temporaryColor,
            durationSeconds = state.colorDurationSeconds,
            digitColor = state.digitColor,
            badgeContrast = state.badgeContrast,
            digitWeight = state.digitWeight,
            digitContrast = state.digitContrast,
        ),
        modifier = Modifier.padding(horizontal = Spacing.gutter).then(modifier),
    )
}

/** The display page repeats the connection card only while it explains why rows are disabled. */
@Composable
private fun UnsettledConnection(state: SettingsStore.State, onRetry: () -> Unit, modifier: Modifier) {
    if (!state.status.isSettled()) {
        Spacer(Modifier.height(Spacing.sm))
        ConnectionCard(
            status = state.status,
            enabledFilters = Integer.bitCount(state.mask),
            onRetry = onRetry,
            modifier = modifier,
        )
        Spacer(Modifier.height(Spacing.sm))
    }
}

/* ---------- Connection ---------- */

private enum class StatusKind { SUCCESS, PROGRESS, WAITING, ERROR }

private fun SettingsStore.Status.kind(): StatusKind = when (this) {
    SettingsStore.Status.READY -> StatusKind.SUCCESS
    SettingsStore.Status.CONNECTING, SettingsStore.Status.SAVING -> StatusKind.PROGRESS
    SettingsStore.Status.WAITING -> StatusKind.WAITING
    SettingsStore.Status.UNAVAILABLE, SettingsStore.Status.SAVE_FAILED -> StatusKind.ERROR
}

/** Connected (ready / saving) shows a compact banner; anything else a centred state block. */
private fun SettingsStore.Status.isSettled(): Boolean =
    this == SettingsStore.Status.READY || this == SettingsStore.Status.SAVING

internal fun statusLabel(status: SettingsStore.Status): Int = when (status) {
    SettingsStore.Status.WAITING -> R.string.settings_waiting
    SettingsStore.Status.CONNECTING -> R.string.settings_connecting
    SettingsStore.Status.READY -> R.string.settings_ready
    SettingsStore.Status.SAVING -> R.string.settings_saving
    SettingsStore.Status.UNAVAILABLE -> R.string.settings_unavailable
    SettingsStore.Status.SAVE_FAILED -> R.string.settings_save_failed
}

private fun stateTitle(status: SettingsStore.Status): Int = when (status) {
    SettingsStore.Status.CONNECTING -> R.string.settings_state_connecting
    SettingsStore.Status.UNAVAILABLE -> R.string.settings_state_unavailable
    SettingsStore.Status.SAVE_FAILED -> R.string.settings_state_save_failed
    else -> R.string.settings_state_waiting
}

@Composable
private fun StatusKind.tone(): Tone {
    val family = Family.colors
    return when (this) {
        StatusKind.SUCCESS -> family.success
        StatusKind.PROGRESS -> family.info
        StatusKind.WAITING -> family.warning
        StatusKind.ERROR -> family.danger
    }
}

private fun StatusKind.icon(): ImageVector = when (this) {
    StatusKind.SUCCESS -> MiuixIcons.Ok
    StatusKind.PROGRESS -> MiuixIcons.Recent
    StatusKind.WAITING -> MiuixIcons.Info
    StatusKind.ERROR -> MiuixIcons.Report
}

@Composable
private fun ConnectionCard(
    status: SettingsStore.Status,
    enabledFilters: Int,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduce = reduceMotion
    GroupCard(modifier = modifier, padded = true) {
        AnimatedContent(
            targetState = status.isSettled(),
            transitionSpec = { Motion.fadeThrough(reduce) },
            label = "connectionLayout",
        ) { settled ->
            if (settled) {
                StatusBanner(status)
            } else {
                val kind = status.kind()
                StateBlock(
                    icon = kind.icon(),
                    tone = kind.tone(),
                    title = stringResource(stateTitle(status)),
                    message = { StatusText(status, TextAlign.Center) },
                    action = {
                        FamilyButton(
                            text = stringResource(R.string.settings_retry),
                            onClick = onRetry,
                            enabled = status != SettingsStore.Status.CONNECTING,
                            testTag = "settings-retry",
                            kind = ButtonKind.PRIMARY,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                    modifier = Modifier.padding(vertical = Spacing.xs),
                )
            }
        }
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = stringResource(R.string.settings_selected, enabledFilters),
            modifier = Modifier.fillMaxWidth().testTag("settings-selected"),
            style = TypeRole.caption.tabular(),
            color = textSecondary,
            textAlign = if (status.isSettled()) TextAlign.Start else TextAlign.Center,
        )
    }
}

/** Compact connected state: tonal status icon + status text, both fading through on change. */
@Composable
private fun StatusBanner(status: SettingsStore.Status) {
    val reduce = reduceMotion
    val kind = status.kind()
    val tone = kind.tone()
    val container = animateColorAsState(tone.container, Motion.effects(reduce), label = "bannerContainer")
    val content = animateColorAsState(tone.content, Motion.effects(reduce), label = "bannerContent")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(Dimens.leadingIcon).clip(CircleShape).drawBehind { drawRect(container.value) },
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(targetState = kind, transitionSpec = { Motion.fadeThrough(reduce) }, label = "bannerIcon") {
                Icon(
                    imageVector = it.icon(),
                    contentDescription = null,
                    modifier = Modifier.size(Dimens.leadingGlyph),
                    tint = content.value,
                )
            }
        }
        Spacer(Modifier.width(Spacing.sm))
        Box(Modifier.weight(1f)) { StatusText(status, TextAlign.Start) }
    }
}

@Composable
private fun StatusText(status: SettingsStore.Status, align: TextAlign) {
    val reduce = reduceMotion
    AnimatedContent(targetState = status, transitionSpec = { Motion.fadeThrough(reduce) }, label = "statusText") {
        Text(
            text = stringResource(statusLabel(it)),
            modifier = Modifier.fillMaxWidth().testTag("settings-status").semantics {
                liveRegion = LiveRegionMode.Polite
            },
            style = TypeRole.subhead,
            color = textSecondary,
            textAlign = align,
        )
    }
}

/* ---------- Display style ---------- */

@Composable
private fun DisplaySection(
    state: SettingsStore.State,
    editable: Boolean,
    onIconColorChange: (Boolean) -> Unit,
    onTemporaryColorChange: (Boolean) -> Unit,
    onColorDurationChange: (Int) -> Unit,
    onDigitColorChange: (Int) -> Unit,
    onBadgeContrastChange: (Int) -> Unit,
    onDigitWeightChange: (Int) -> Unit,
    onDigitContrastChange: (Int) -> Unit,
) {
    GroupCard {
        SwitchRow(
            icon = MiuixIcons.Theme,
            title = stringResource(R.string.settings_icon_color_title),
            summary = stringResource(R.string.settings_icon_color_description),
            checked = state.iconColorEnabled,
            enabled = editable,
            onCheckedChange = onIconColorChange,
            testTag = "icon-color-switch",
        )
        val temporaryEditable = editable && state.iconColorEnabled
        SwitchRow(
            icon = MiuixIcons.Recent,
            title = stringResource(R.string.settings_temporary_color_title),
            summary = stringResource(R.string.settings_temporary_color_description),
            checked = state.temporaryColor,
            enabled = temporaryEditable,
            onCheckedChange = onTemporaryColorChange,
            testTag = "temporary-color-switch",
        )
        val durations = FilterPreferences.COLOR_DURATIONS
        DropdownRow(
            icon = MiuixIcons.Stopwatch,
            title = stringResource(R.string.settings_color_duration_title),
            summary = stringResource(R.string.settings_color_duration_description),
            labels = durations.map { stringResource(R.string.settings_color_seconds, it) },
            selectedIndex = durations.indexOf(state.colorDurationSeconds),
            enabled = temporaryEditable && state.temporaryColor,
            testTag = "icon-color-duration",
            onSelect = { onColorDurationChange(durations[it]) },
        )
        val colorStyleEditable = editable && state.iconColorEnabled
        StyleRow(
            icon = MiuixIcons.Edit,
            title = stringResource(R.string.settings_digit_color_title),
            summary = stringResource(R.string.settings_digit_color_description),
            labels = listOf(
                stringResource(R.string.settings_digit_color_auto),
                stringResource(R.string.settings_digit_color_white),
                stringResource(R.string.settings_digit_color_black),
                stringResource(R.string.settings_digit_color_transparent),
            ),
            values = FilterPreferences.DIGIT_COLORS,
            selected = state.digitColor,
            enabled = colorStyleEditable,
            tag = "digit-color",
            onChange = onDigitColorChange,
        )
        StyleRow(
            icon = MiuixIcons.Show,
            title = stringResource(R.string.settings_digit_contrast_title),
            summary = stringResource(R.string.settings_digit_contrast_description),
            labels = listOf(
                stringResource(R.string.settings_digit_contrast_standard),
                stringResource(R.string.settings_digit_contrast_high),
                stringResource(R.string.settings_digit_contrast_max),
            ),
            values = FilterPreferences.DIGIT_CONTRASTS,
            selected = state.digitContrast,
            enabled = colorStyleEditable && state.digitColor == FilterPreferences.DIGIT_COLOR_AUTO,
            tag = "digit-contrast",
            onChange = onDigitContrastChange,
        )
        StyleRow(
            icon = MiuixIcons.Show,
            title = stringResource(R.string.settings_badge_contrast_title),
            summary = stringResource(R.string.settings_badge_contrast_description),
            labels = listOf(
                stringResource(R.string.settings_badge_contrast_soft),
                stringResource(R.string.settings_badge_contrast_standard),
                stringResource(R.string.settings_badge_contrast_high),
            ),
            values = FilterPreferences.BADGE_CONTRASTS,
            selected = state.badgeContrast,
            enabled = colorStyleEditable,
            tag = "badge-contrast",
            onChange = onBadgeContrastChange,
        )
        StyleRow(
            icon = MiuixIcons.Tune,
            title = stringResource(R.string.settings_digit_weight_title),
            summary = stringResource(R.string.settings_digit_weight_description),
            labels = listOf(
                stringResource(R.string.settings_digit_weight_normal),
                stringResource(R.string.settings_digit_weight_medium),
                stringResource(R.string.settings_digit_weight_bold),
            ),
            values = FilterPreferences.DIGIT_WEIGHTS,
            selected = state.digitWeight,
            enabled = editable,
            tag = "digit-weight",
            onChange = onDigitWeightChange,
        )
    }
}

/** Short exclusive badge style choice as a segmented control. */
@Composable
private fun StyleRow(
    icon: ImageVector,
    title: String,
    summary: String,
    labels: List<String>,
    values: List<Int>,
    selected: Int,
    enabled: Boolean,
    tag: String,
    onChange: (Int) -> Unit,
) {
    SegmentedRow(
        icon = icon,
        title = title,
        summary = summary,
        labels = labels,
        selectedIndex = values.indexOf(selected).coerceAtLeast(0),
        enabled = enabled,
        testTag = tag,
        onSelect = { onChange(values[it]) },
    )
}

/* ---------- Filters ---------- */

@Composable
private fun FilterSection(
    group: FilterGroup,
    mask: Int,
    editable: Boolean,
    onFilterChange: (NotificationType, Boolean) -> Unit,
) {
    GroupCard {
        group.types.forEach { type ->
            val (titleId, descriptionId) = filterLabels(type)
            SwitchRow(
                icon = filterIcon(type),
                title = stringResource(titleId),
                summary = stringResource(descriptionId),
                checked = mask and type.bit != 0,
                enabled = editable,
                onCheckedChange = { onFilterChange(type, it) },
                testTag = "filter-${type.key}",
            )
        }
    }
}
