package dev.hyperos.notificationcount.settings

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.TextStyles

/*
 * Design tokens of the cross-app design system v2 (DESIGN_SYSTEM.md). Values are fixed by
 * the spec; names match the other apps of the family. Miuix cards already draw
 * continuous-curvature corners, so radii are passed straight to `Card`.
 */

internal object Spacing {
    val xxs = 4.dp
    val xs = 8.dp
    /** Card gap, gap between elements of one group. */
    val sm = 12.dp
    /** Page gutter and card padding. */
    val md = 16.dp
    /** Gap between sections. */
    val lg = 24.dp
    val xl = 32.dp

    val gutter = md
    val cardPadding = md
    /** Section headers align with the text inside cards: gutter + card padding. */
    val headerInset = gutter + cardPadding
}

internal object Radius {
    val card = 24.dp
    val row = 16.dp
    val button = 16.dp
    val control = 14.dp
    val small = 10.dp
    val sheet = 28.dp
}

internal object Dimens {
    val rowMinHeight = 56.dp
    val minTouch = 48.dp
    val primaryButtonHeight = 52.dp
    val leadingIcon = 36.dp
    val leadingGlyph = 20.dp
    val stateIcon = 64.dp
    val segmentedHeight = 36.dp
    val previewBar = 48.dp
    val hairline = 1.dp
}

/** Text emphasis, applied as alpha on onSurface. */
internal object Emphasis {
    const val PRIMARY = 0.92f
    const val SECONDARY = 0.62f
    const val TERTIARY = 0.42f
    const val DISABLED = 0.38f
}

/** Semantic chip/banner tone: low-saturation container with a high-contrast foreground. */
@Immutable
internal data class Tone(val container: Color, val content: Color)

@Immutable
internal data class FamilyColors(
    val page: Color,
    val card: Color,
    val raised: Color,
    val hairline: Color,
    val scrim: Color,
    /** Press state layer drawn over pressed surfaces. */
    val stateLayer: Color,
    val fallbackAccent: Color,
    val success: Tone,
    val warning: Tone,
    val danger: Tone,
    val info: Tone,
    val neutral: Tone,
    /** Example app icon colour used only by the live preview. */
    val sampleAppColor: Int,
)

private val Light = FamilyColors(
    page = Color(0xFFF5F6F8),
    card = Color(0xFFFFFFFF),
    raised = Color(0xFFFFFFFF),
    hairline = Color(0x14000000),
    scrim = Color(0x52000000),
    stateLayer = Color(0x0F000000),
    fallbackAccent = Color(0xFF3478F6),
    success = Tone(Color(0xFFE6F4EA), Color(0xFF1B6B34)),
    warning = Tone(Color(0xFFFDF1DC), Color(0xFF8A5300)),
    danger = Tone(Color(0xFFFCE8E8), Color(0xFFB3261E)),
    info = Tone(Color(0xFFE5EEFD), Color(0xFF1A57C9)),
    neutral = Tone(Color(0xFFEEF0F3), Color(0xFF4A4F57)),
    sampleAppColor = 0xFF03D769.toInt(),
)

private val Dark = FamilyColors(
    page = Color(0xFF000000),
    card = Color(0xFF16171A),
    raised = Color(0xFF212226),
    hairline = Color(0x1AFFFFFF),
    scrim = Color(0x80000000),
    stateLayer = Color(0x0FFFFFFF),
    fallbackAccent = Color(0xFF5A93FF),
    success = Tone(Color(0xFF17351F), Color(0xFF8FD9A8)),
    warning = Tone(Color(0xFF3A2B10), Color(0xFFF5C36B)),
    danger = Tone(Color(0xFF3B1A1A), Color(0xFFF4A7A3)),
    info = Tone(Color(0xFF172947), Color(0xFFA9C5FA)),
    neutral = Tone(Color(0xFF26282D), Color(0xFFC4C8CF)),
    sampleAppColor = 0xFF03D769.toInt(),
)

internal object Family {
    val colors: FamilyColors
        @Composable get() = if (isSystemInDarkTheme()) Dark else Light

    fun colors(dark: Boolean): FamilyColors = if (dark) Dark else Light
}

/** Text colour at an emphasis level on the current surface. */
internal val textPrimary: Color
    @Composable get() = MiuixTheme.colorScheme.onSurface.copy(alpha = Emphasis.PRIMARY)
internal val textSecondary: Color
    @Composable get() = MiuixTheme.colorScheme.onSurface.copy(alpha = Emphasis.SECONDARY)
internal val textTertiary: Color
    @Composable get() = MiuixTheme.colorScheme.onSurface.copy(alpha = Emphasis.TERTIARY)

/**
 * Type scale mapped onto the Miuix roles the library components read:
 * title1 = largeTitle (TopAppBar), title3 = collapsed title, headline1 = row/card headline,
 * body1 = body, body2 = subhead (row summaries), subtitle = sectionHeader (SmallTitle),
 * footnote1 = caption.
 */
internal fun familyTextStyles(base: TextStyles): TextStyles = base.copy(
    title1 = base.title1.copy(fontSize = 30.sp, fontWeight = FontWeight.Bold),
    title2 = base.title2.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    title3 = base.title3.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
    headline1 = base.headline1.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
    body1 = base.body1.copy(fontSize = 16.sp, fontWeight = FontWeight.Normal),
    body2 = base.body2.copy(fontSize = 14.sp, fontWeight = FontWeight.Normal),
    subtitle = base.subtitle.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
    footnote1 = base.footnote1.copy(fontSize = 12.sp, fontWeight = FontWeight.Normal),
)

internal object TypeRole {
    val title: TextStyle @Composable get() = MiuixTheme.textStyles.title2
    val headline: TextStyle @Composable get() = MiuixTheme.textStyles.headline1
    val body: TextStyle @Composable get() = MiuixTheme.textStyles.body1
    val subhead: TextStyle @Composable get() = MiuixTheme.textStyles.body2
    val sectionHeader: TextStyle @Composable get() = MiuixTheme.textStyles.subtitle
    val caption: TextStyle @Composable get() = MiuixTheme.textStyles.footnote1
}

/** Tabular figures: counters and the clock never jitter when digits change. */
internal fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")
