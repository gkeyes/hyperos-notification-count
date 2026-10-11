package dev.hyperos.notificationcount.settings

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.LocalColors
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.defaultTextStyles

/**
 * Miuix theme aligned with the design system: the accent comes from the wallpaper
 * (Miuix Monet), while page, card, raised, hairline and scrim surfaces use the family
 * values so every app of the family reads the same. Follows the system light/dark mode.
 */
@Composable
internal fun NotificationCountTheme(content: @Composable () -> Unit) {
    val controller = remember { ThemeController(ColorSchemeMode.MonetSystem) }
    val textStyles = remember { familyTextStyles(defaultTextStyles()) }
    MiuixTheme(controller = controller, textStyles = textStyles) {
        val dark = isSystemInDarkTheme()
        val monet = MiuixTheme.colorScheme
        val family = Family.colors(dark)
        val colors = remember(monet, dark) {
            monet.copy(
                background = family.page,
                surface = family.card,
                surfaceContainer = family.card,
                surfaceContainerHigh = family.raised,
                dividerLine = family.hairline,
                windowDimming = family.scrim,
            )
        }
        CompositionLocalProvider(LocalColors provides colors) {
            CompositionLocalProvider(LocalReduceMotion provides rememberReduceMotion(), content = content)
        }
    }
}
