package dev.hyperos.notificationcount.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.util.function.Consumer
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/** Only the module manager exposes this activity; the manifest has no launcher category. */
class SettingsActivity : ComponentActivity() {
    private lateinit var store: SettingsStore
    private var screenState by mutableStateOf<SettingsStore.State?>(null)
    private val observer = Consumer<SettingsStore.State> { screenState = it }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        store = (application as ModuleApplication).settingsStore
        screenState = store.state()
        setContent {
            val theme = remember { ThemeController(ColorSchemeMode.System) }
            MiuixTheme(controller = theme) {
                screenState?.let { state ->
                    SettingsScreen(
                        state = state,
                        onFilterChange = store::setExcluded,
                        onIconColorChange = store::setIconColorEnabled,
                        onReset = store::reset,
                        onRetry = store::retry,
                        onBack = ::finish,
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        store.observe(observer)
    }

    override fun onStop() {
        store.removeObserver(observer)
        super.onStop()
    }
}
