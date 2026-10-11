package dev.hyperos.notificationcount.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.hyperos.notificationcount.R
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/* ---------- Section header + group card ---------- */

/**
 * Section title above its card, 13sp Medium in the secondary text colour, aligned with the
 * text inside cards. An optional count chip rolls its digits when the value changes.
 */
@Composable
internal fun SectionHeader(title: String, modifier: Modifier = Modifier, enabled: Int? = null, total: Int = 0) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = Spacing.lg, bottom = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SmallTitle(
            text = title,
            modifier = Modifier.weight(1f),
            textColor = textSecondary,
            insideMargin = PaddingValues(start = Spacing.headerInset, end = Spacing.xs),
        )
        if (enabled != null) {
            CountChip(enabled = enabled, total = total, modifier = Modifier.padding(end = Spacing.headerInset))
        }
    }
}

/** Card holding rows. No dividers: rows are separated by their 56dp height and insets. */
@Composable
internal fun GroupCard(modifier: Modifier = Modifier, padded: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.gutter),
        cornerRadius = Radius.card,
        insideMargin = if (padded) PaddingValues(Spacing.cardPadding) else PaddingValues(0.dp),
        colors = CardDefaults.defaultColors(color = Family.colors.card),
        content = content,
    )
}

/* ---------- Chips ---------- */

/** Fully rounded semantic chip; colour cross-fades and the label fades through on change. */
@Composable
internal fun StatusChip(label: String, tone: Tone, modifier: Modifier = Modifier) {
    val reduce = reduceMotion
    val container = animateColorAsState(tone.container, Motion.effects(reduce), label = "chipContainer")
    val content = animateColorAsState(tone.content, Motion.effects(reduce), label = "chipContent")
    Box(
        modifier
            .clip(CircleShape)
            .drawBehind { drawRect(container.value) }
            .padding(horizontal = Spacing.sm - 2.dp, vertical = Spacing.xxs),
    ) {
        AnimatedContent(targetState = label, transitionSpec = { Motion.fadeThrough(reduce) }, label = "chipLabel") {
            Text(text = it, style = TypeRole.caption.tabular(), fontWeight = FontWeight.Medium, color = content.value)
        }
    }
}

@Composable
private fun CountChip(enabled: Int, total: Int, modifier: Modifier = Modifier) {
    val palette = Family.colors
    val reduce = reduceMotion
    val tone = if (enabled > 0) palette.info else palette.neutral
    val container = animateColorAsState(tone.container, Motion.effects(reduce), label = "countContainer")
    val content = animateColorAsState(tone.content, Motion.effects(reduce), label = "countContent")
    val description = stringResource(R.string.settings_group_count_description, enabled, total)
    Row(
        modifier
            .clip(CircleShape)
            .drawBehind { drawRect(container.value) }
            .padding(horizontal = Spacing.xs + 2.dp, vertical = 2.dp)
            .clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedContent(
            targetState = enabled,
            transitionSpec = { Motion.numberRoll(up = targetState > initialState, reduce = reduce) },
            label = "groupCount",
        ) { value ->
            Text(text = value.toString(), style = TypeRole.caption.tabular(), fontWeight = FontWeight.Medium, color = content.value)
        }
        Text(
            text = stringResource(R.string.settings_group_count_total, total),
            style = TypeRole.caption.tabular(),
            fontWeight = FontWeight.Medium,
            color = content.value,
        )
    }
}

/* ---------- Rows ---------- */

/** 36dp leading tile with a radius-10 tonal background; tone cross-fades with row state. */
@Composable
internal fun LeadingIcon(icon: ImageVector, active: Boolean, enabled: Boolean = true) {
    val palette = Family.colors
    val reduce = reduceMotion
    val tone = if (active) palette.info else palette.neutral
    val container = animateColorAsState(tone.container, Motion.effects(reduce), label = "iconContainer")
    val content = animateColorAsState(tone.content, Motion.effects(reduce), label = "iconContent")
    Box(
        Modifier
            .padding(end = Spacing.sm)
            .size(Dimens.leadingIcon)
            .graphicsLayer { alpha = if (enabled) 1f else Emphasis.DISABLED / Emphasis.PRIMARY }
            .clip(RoundedCornerShape(Radius.small))
            .drawBehind { drawRect(container.value) },
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(Dimens.leadingGlyph), tint = content.value)
    }
}

/**
 * Miuix switch row with a leading icon, exposed to accessibility as one switch rather than
 * separate row and toggle targets. Miuix plays the toggle haptic itself.
 */
@Composable
internal fun SwitchRow(
    icon: ImageVector,
    title: String,
    summary: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
) {
    SwitchPreference(
        title = title,
        summary = summary,
        checked = checked,
        enabled = enabled,
        onCheckedChange = onCheckedChange,
        startAction = { LeadingIcon(icon, active = checked, enabled = enabled) },
        modifier = Modifier
            .heightIn(min = Dimens.rowMinHeight)
            .observedPressFeedback(enabled)
            .testTag(testTag)
            .clearAndSetSemantics {
                contentDescription = "$title。$summary"
                role = Role.Switch
                toggleableState = if (checked) ToggleableState.On else ToggleableState.Off
                if (enabled) {
                    onClick {
                        onCheckedChange(!checked)
                        true
                    }
                } else {
                    disabled()
                }
            },
    )
}

/** Miuix dropdown row (anchored menu with its own haptic) with a leading icon. */
@Composable
internal fun DropdownRow(
    icon: ImageVector,
    title: String,
    summary: String,
    labels: List<String>,
    selectedIndex: Int,
    enabled: Boolean,
    testTag: String,
    onSelect: (Int) -> Unit,
) {
    val label = labels[selectedIndex.coerceIn(0, labels.lastIndex)]
    OverlayDropdownPreference(
        title = title,
        summary = summary,
        items = labels,
        selectedIndex = selectedIndex,
        enabled = enabled,
        startAction = { LeadingIcon(icon, active = enabled, enabled = enabled) },
        onSelectedIndexChange = onSelect,
        modifier = Modifier
            .heightIn(min = Dimens.rowMinHeight)
            .observedPressFeedback(enabled)
            .testTag(testTag)
            .semantics {
                stateDescription = label
                // Miuix removes the click handler while disabled; expose that state to accessibility.
                if (!enabled) disabled()
            },
    )
}

/**
 * Row with a segmented control under its text, for short exclusive choices. The selection
 * indicator slides with the spatial spring; each pick plays a segment tick.
 */
@Composable
internal fun SegmentedRow(
    icon: ImageVector,
    title: String,
    summary: String,
    labels: List<String>,
    selectedIndex: Int,
    enabled: Boolean,
    testTag: String,
    onSelect: (Int) -> Unit,
) {
    val selected = selectedIndex.coerceIn(0, labels.lastIndex)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.sm)
            .testTag(testTag)
            .semantics {
                stateDescription = labels[selected]
                if (!enabled) disabled()
            },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LeadingIcon(icon, active = enabled, enabled = enabled)
            Column(Modifier.weight(1f)) {
                val alpha = if (enabled) 1f else Emphasis.DISABLED / Emphasis.PRIMARY
                Text(text = title, style = TypeRole.headline, color = textPrimary.copy(alpha = Emphasis.PRIMARY * alpha))
                Text(text = summary, style = TypeRole.subhead, color = textSecondary.copy(alpha = Emphasis.SECONDARY * alpha))
            }
        }
        Spacer(Modifier.height(Spacing.sm))
        SegmentedControl(labels, selected, enabled, onSelect)
    }
}

@Composable
private fun SegmentedControl(labels: List<String>, selected: Int, enabled: Boolean, onSelect: (Int) -> Unit) {
    val palette = Family.colors
    val reduce = reduceMotion
    val haptics = LocalHapticFeedback.current
    val inset = 3.dp
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(Dimens.segmentedHeight)
            .clip(RoundedCornerShape(Radius.control))
            .drawBehind { drawRect(palette.page) }
            .graphicsLayer { alpha = if (enabled) 1f else Emphasis.DISABLED / Emphasis.PRIMARY },
    ) {
        val segment = (maxWidth - inset * 2) / labels.size
        val indicatorX = animateDpAsState(segment * selected, Motion.spatial(reduce), label = "segment")
        Box(
            Modifier
                .offset { IntOffset((inset + indicatorX.value).roundToPx(), inset.roundToPx()) }
                .width(segment)
                .height(Dimens.segmentedHeight - inset * 2)
                .clip(RoundedCornerShape(Radius.control - inset))
                .drawBehind { drawRect(palette.raised) }
                .border(Dimens.hairline, palette.hairline, RoundedCornerShape(Radius.control - inset)),
        )
        Row(Modifier.fillMaxHeight().padding(horizontal = inset).selectableGroup()) {
            labels.forEachIndexed { index, label ->
                val isSelected = index == selected
                val color = animateColorAsState(
                    if (isSelected) textPrimary else textSecondary, Motion.effects(reduce), label = "segmentText",
                )
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .selectable(
                            selected = isSelected,
                            enabled = enabled,
                            role = Role.RadioButton,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            if (!isSelected) {
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                onSelect(index)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = TypeRole.subhead,
                        fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                        color = color.value,
                    )
                }
            }
        }
    }
}

/* ---------- Buttons ---------- */

internal enum class ButtonKind { PRIMARY, SECONDARY, DESTRUCTIVE, TEXT }

/**
 * Family button on Miuix TextButton: primary (accent fill, 52dp), secondary (accent 12%
 * tonal), destructive (danger tone) or text. All press; primary/destructive confirm.
 */
@Composable
internal fun FamilyButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    testTag: String,
    kind: ButtonKind,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current
    val accent = MiuixTheme.colorScheme.primary
    val palette = Family.colors
    val disabledText = MiuixTheme.colorScheme.onSurface.copy(alpha = Emphasis.DISABLED)
    val colors = when (kind) {
        ButtonKind.PRIMARY -> ButtonDefaults.textButtonColors(
            color = accent, disabledColor = palette.neutral.container,
            textColor = MiuixTheme.colorScheme.onPrimary, disabledTextColor = disabledText,
        )
        ButtonKind.SECONDARY -> ButtonDefaults.textButtonColors(
            color = accent.copy(alpha = 0.12f), disabledColor = palette.neutral.container,
            textColor = accent, disabledTextColor = disabledText,
        )
        ButtonKind.DESTRUCTIVE -> ButtonDefaults.textButtonColors(
            color = palette.danger.container, disabledColor = palette.neutral.container,
            textColor = palette.danger.content, disabledTextColor = disabledText,
        )
        ButtonKind.TEXT -> ButtonDefaults.textButtonColors(
            color = Color.Transparent, disabledColor = Color.Transparent,
            textColor = accent, disabledTextColor = disabledText,
        )
    }
    TextButton(
        text = text,
        onClick = {
            if (kind == ButtonKind.PRIMARY || kind == ButtonKind.DESTRUCTIVE) {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            }
            onClick()
        },
        enabled = enabled,
        cornerRadius = Radius.button,
        minHeight = if (kind == ButtonKind.PRIMARY) Dimens.primaryButtonHeight else Dimens.minTouch,
        colors = colors,
        interactionSource = interaction,
        modifier = modifier.pressFeedback(interaction, enabled).testTag(testTag),
    )
}

/* ---------- Text ---------- */

/** Quiet explanatory text between sections, aligned with the text inside cards. */
@Composable
internal fun Footnote(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = TypeRole.caption,
        color = textSecondary,
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.headerInset),
    )
}

/* ---------- State block ---------- */

/**
 * Empty / loading / error layout: 64dp circular tonal icon, headline, one subhead line and
 * an optional action, centred in the space it is given.
 */
@Composable
internal fun StateBlock(
    icon: ImageVector,
    tone: Tone,
    title: String,
    message: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    val reduce = reduceMotion
    val container = animateColorAsState(tone.container, Motion.effects(reduce), label = "stateContainer")
    val content = animateColorAsState(tone.content, Motion.effects(reduce), label = "stateContent")
    Column(
        modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Box(
            Modifier.size(Dimens.stateIcon).clip(CircleShape).drawBehind { drawRect(container.value) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(28.dp), tint = content.value)
        }
        Spacer(Modifier.height(Spacing.xxs))
        Text(text = title, style = TypeRole.headline, color = textPrimary)
        message()
        if (action != null) {
            Spacer(Modifier.height(Spacing.xs))
            action()
        }
    }
}
