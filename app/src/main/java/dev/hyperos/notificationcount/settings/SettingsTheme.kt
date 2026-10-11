package dev.hyperos.notificationcount.settings

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.defaultTextStyles
import top.yukonga.miuix.kmp.theme.lightColorScheme

/**
 * Miuix theme aligned with the design system: the accent is the system dynamic (wallpaper)
 * accent, falling back to the family accent; page, card, raised, hairline and scrim use the
 * family surfaces so every app of the family reads the same. Follows system light/dark.
 */
@Composable
internal fun NotificationCountTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val controller = remember(context) {
        val light = Family.colors(dark = false)
        val dark = Family.colors(dark = true)
        ThemeController(
            colorSchemeMode = ColorSchemeMode.System,
            lightColors = lightColorScheme(
                primary = context.dynamicAccent(android.R.color.system_accent1_600, light.fallbackAccent),
                background = light.page,
                surface = light.card,
                surfaceContainer = light.card,
                surfaceContainerHigh = light.raised,
                dividerLine = light.hairline,
                windowDimming = light.scrim,
            ),
            darkColors = darkColorScheme(
                primary = context.dynamicAccent(android.R.color.system_accent1_400, dark.fallbackAccent),
                background = dark.page,
                surface = dark.card,
                surfaceContainer = dark.card,
                surfaceContainerHigh = dark.raised,
                dividerLine = dark.hairline,
                windowDimming = dark.scrim,
            ),
        )
    }
    val textStyles = remember { familyTextStyles(defaultTextStyles()) }
    MiuixTheme(controller = controller, textStyles = textStyles) {
        CompositionLocalProvider(LocalReduceMotion provides rememberReduceMotion(), content = content)
    }
}

private fun Context.dynamicAccent(resource: Int, fallback: Color): Color =
    runCatching { Color(getColor(resource)) }.getOrDefault(fallback)
