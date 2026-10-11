package dev.hyperos.notificationcount.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hyperos.notificationcount.R
import dev.hyperos.notificationcount.core.BadgeStyle
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

/** Preview counts cycle 1..9, then the overflow glyph, matching the real display rules. */
private const val OVERFLOW = 10
private const val INITIAL_COUNT = 3

// Disc geometry of the solid knockout vectors (16x16 viewport), as used by CountDrawable.
private const val VIEWPORT = 16f
private const val DISC_CX = 7f
private const val DISC_CY = 8.7f
private const val GLYPH_FILL_RADIUS = 5.5f

/** Everything the preview needs from the saved settings; read-only. */
internal data class BadgePreviewSettings(
    val iconColorEnabled: Boolean,
    val temporaryColor: Boolean,
    val durationSeconds: Int,
    val digitColor: Int,
    val badgeContrast: Int,
    val digitWeight: Int,
)

/**
 * A miniature status bar drawing the module's real solid knockout vectors with the saved
 * style: digit weight, badge colour through the same [BadgeStyle] contrast rules the
 * renderer uses, and the digit fill (automatic / white / black / knocked out). Tapping the
 * card simulates a new notification locally; the store is never touched.
 */
@Composable
internal fun CountBadgePreview(settings: BadgePreviewSettings, modifier: Modifier = Modifier) {
    var count by rememberSaveable { mutableIntStateOf(INITIAL_COUNT) }
    var arrivals by rememberSaveable { mutableIntStateOf(0) }
    var colorWindowOpen by remember { mutableStateOf(false) }

    // Mirrors temporary mode: enabling it, changing the duration or a new arrival restarts
    // the window; when it ends the badge returns to the system tint.
    LaunchedEffect(arrivals, settings.iconColorEnabled, settings.temporaryColor, settings.durationSeconds) {
        if (settings.iconColorEnabled && settings.temporaryColor) {
            colorWindowOpen = true
            delay(settings.durationSeconds * 1000L)
        }
        colorWindowOpen = false
    }

    val family = Family.colors
    val reduce = reduceMotion
    val colored = settings.iconColorEnabled && (!settings.temporaryColor || colorWindowOpen)
    val barColor = MiuixTheme.colorScheme.background
    val systemTint = MiuixTheme.colorScheme.onBackground
    val style = BadgeStyle(settings.digitColor, settings.badgeContrast, settings.digitWeight)
    val candidate: Int? = if (colored) family.sampleAppColor else null
    val badgeArgb = style.badgeColor(candidate, systemTint.toArgb())
    val glyphArgb: Int? = style.glyphColor(candidate, badgeArgb)
    val badgeTint = animateColorAsState(Color(badgeArgb), Motion.effects(reduce), label = "badgeTint")
    val glyphFill = animateColorAsState(
        if (glyphArgb != null) Color(glyphArgb) else Color(badgeArgb).copy(alpha = 0f),
        Motion.effects(reduce),
        label = "glyphFill",
    )

    val countLabel = if (count == OVERFLOW) stringResource(R.string.settings_preview_overflow)
    else stringResource(R.string.settings_preview_count, count)
    val modeLabel = when {
        !settings.iconColorEnabled -> stringResource(R.string.settings_preview_mono)
        settings.temporaryColor -> stringResource(R.string.settings_preview_temporary, settings.durationSeconds)
        else -> stringResource(R.string.settings_preview_app_color)
    }
    val description = stringResource(R.string.settings_preview_description, countLabel, modeLabel)
    val simulateArrival: () -> Unit = {
        count = if (count >= OVERFLOW) 1 else count + 1
        arrivals++
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("settings-preview")
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                onClick {
                    simulateArrival()
                    true
                }
            },
        cornerRadius = Radius.card,
        insideMargin = PaddingValues(Spacing.cardPadding),
        colors = CardDefaults.defaultColors(color = family.card),
        pressFeedbackType = PressFeedbackType.Sink,
        onClick = simulateArrival,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.settings_preview_title),
                style = TypeRole.headline,
                color = textPrimary,
                modifier = Modifier.weight(1f),
            )
            StatusChip(label = modeLabel, tone = if (settings.iconColorEnabled) family.success else family.neutral)
        }
        Spacer(Modifier.height(Spacing.sm))
        Card(
            cornerRadius = Radius.row,
            insideMargin = PaddingValues(horizontal = Spacing.md),
            colors = CardDefaults.defaultColors(color = barColor),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(Dimens.previewBar),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.settings_preview_clock),
                    style = TypeRole.headline.tabular(),
                    fontSize = 17.sp,
                    color = systemTint,
                )
                Spacer(Modifier.width(Spacing.xxs + 2.dp))
                CountBadge(count, settings.digitWeight, badgeTint, glyphFill, reduce)
                Spacer(Modifier.weight(1f))
                BatteryGlyph(color = systemTint)
            }
        }
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = stringResource(R.string.settings_preview_hint),
            style = TypeRole.caption,
            color = textSecondary,
        )
    }
}

/** Badge sized ~1.2x the clock digits, like the renderer. */
private val BadgeSize = 22.dp

@Composable
private fun CountBadge(count: Int, weight: Int, tint: State<Color>, glyphFill: State<Color>, reduce: Boolean) {
    AnimatedContent(
        targetState = count to weight,
        transitionSpec = {
            if (targetState.first != initialState.first) badgeArrival(reduce) else Motion.fadeThrough(reduce)
        },
        contentAlignment = Alignment.Center,
        label = "badgeCount",
    ) { (value, valueWeight) ->
        val painter = painterResource(badgeDrawable(value, valueWeight))
        // Colours are read in the draw phase, so colour springs never recompose the badge.
        Box(
            Modifier.size(BadgeSize).drawBehind {
                val scale = size.width / VIEWPORT
                val fill = glyphFill.value
                if (fill.alpha > 0f) {
                    // Under the disc: only the knocked-out digit opening shows it.
                    drawCircle(fill, radius = GLYPH_FILL_RADIUS * scale, center = Offset(DISC_CX * scale, DISC_CY * scale))
                }
                with(painter) { draw(size, colorFilter = ColorFilter.tint(tint.value)) }
            },
        )
    }
}

private fun badgeArrival(reduce: Boolean): ContentTransform =
    if (reduce) {
        EnterTransition.None togetherWith ExitTransition.None
    } else {
        ContentTransform(
            targetContentEnter = scaleIn(Motion.spatial(), initialScale = 0.6f) + fadeIn(Motion.effects()),
            initialContentExit = scaleOut(Motion.spatial(), targetScale = 1.2f) + fadeOut(Motion.effects()),
            sizeTransform = SizeTransform(clip = false),
        )
    }

private val NORMAL = intArrayOf(
    R.drawable.notification_count_1, R.drawable.notification_count_2, R.drawable.notification_count_3,
    R.drawable.notification_count_4, R.drawable.notification_count_5, R.drawable.notification_count_6,
    R.drawable.notification_count_7, R.drawable.notification_count_8, R.drawable.notification_count_9,
    R.drawable.notification_count_overflow,
)
private val MEDIUM = intArrayOf(
    R.drawable.notification_count_1_medium, R.drawable.notification_count_2_medium,
    R.drawable.notification_count_3_medium, R.drawable.notification_count_4_medium,
    R.drawable.notification_count_5_medium, R.drawable.notification_count_6_medium,
    R.drawable.notification_count_7_medium, R.drawable.notification_count_8_medium,
    R.drawable.notification_count_9_medium, R.drawable.notification_count_overflow_medium,
)
private val BOLD = intArrayOf(
    R.drawable.notification_count_1_bold, R.drawable.notification_count_2_bold,
    R.drawable.notification_count_3_bold, R.drawable.notification_count_4_bold,
    R.drawable.notification_count_5_bold, R.drawable.notification_count_6_bold,
    R.drawable.notification_count_7_bold, R.drawable.notification_count_8_bold,
    R.drawable.notification_count_9_bold, R.drawable.notification_count_overflow_bold,
)

private fun badgeDrawable(count: Int, weight: Int): Int {
    val set = when (weight) {
        1 -> MEDIUM
        2 -> BOLD
        else -> NORMAL
    }
    return set[count.coerceIn(1, OVERFLOW) - 1]
}

/** Battery outline so the bar reads as a status bar at a glance. */
@Composable
private fun BatteryGlyph(color: Color) {
    Canvas(Modifier.size(width = 25.dp, height = 12.dp)) {
        val stroke = 1.2.dp.toPx()
        val capWidth = 2.dp.toPx()
        val body = Size(size.width - capWidth - 1.dp.toPx(), size.height)
        drawRoundRect(
            color = color.copy(alpha = 0.45f),
            topLeft = Offset(stroke / 2, stroke / 2),
            size = Size(body.width - stroke, body.height - stroke),
            cornerRadius = CornerRadius(3.dp.toPx()),
            style = Stroke(stroke),
        )
        val inset = 2.2.dp.toPx()
        drawRoundRect(
            color = color,
            topLeft = Offset(inset, inset),
            size = Size((body.width - inset * 2) * 0.72f, body.height - inset * 2),
            cornerRadius = CornerRadius(1.5.dp.toPx()),
        )
        drawRoundRect(
            color = color.copy(alpha = 0.45f),
            topLeft = Offset(size.width - capWidth, size.height * 0.32f),
            size = Size(capWidth, size.height * 0.36f),
            cornerRadius = CornerRadius(1.dp.toPx()),
        )
    }
}
