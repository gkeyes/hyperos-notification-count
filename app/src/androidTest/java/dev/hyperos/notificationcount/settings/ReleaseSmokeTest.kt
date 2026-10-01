package dev.hyperos.notificationcount.settings

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Build
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.hyperos.notificationcount.core.NotificationType
import dev.hyperos.notificationcount.render.CountDrawable
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Runs against the signed, shrunk release APK, with its real Application and provider. */
@RunWith(AndroidJUnit4::class)
class ReleaseSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<SettingsActivity>()

    private lateinit var application: ModuleApplication
    private lateinit var preferences: SharedPreferences

    @Before
    fun connectTestPreferences() {
        assertEquals(37, Build.VERSION.SDK_INT)
        composeRule.runOnIdle {
            application = ApplicationProvider.getApplicationContext()
            preferences = application.getSharedPreferences("release-smoke", Context.MODE_PRIVATE)
            assertTrue(preferences.edit().clear().commit())
            application.settingsStore.disconnect(application)
            application.settingsStore.connect(application) { preferences }
        }
        awaitReady(0)
    }

    @Test
    fun allFiltersPersistReopenAndResetInTheShrunkActivity() {
        assertFilterCount()
        var mask = 0
        for (type in NotificationType.values()) {
            composeRule.onNodeWithTag("filter-${type.key}")
                .performScrollTo().assertIsDisplayed().assertIsEnabled().assertIsOff().performClick()
            mask = mask or type.bit
            awaitReady(mask)
            composeRule.onNodeWithTag("filter-${type.key}").assertIsOn()
            assertEquals(mask, preferences.getInt(FilterPreferences.EXCLUDED_MASK, -1))
        }
        assertEquals(FilterPreferences.KNOWN_MASK, mask)
        composeRule.activityRule.scenario.recreate()
        assertFilterCount()
        for (type in NotificationType.values()) {
            composeRule.onNodeWithTag("filter-${type.key}").assertIsOn()
        }
        composeRule.onNodeWithTag("settings-reset").performScrollTo()
            .assertIsDisplayed().assertIsEnabled().performClick()
        awaitReady(0)
        assertEquals(0, preferences.getInt(FilterPreferences.EXCLUDED_MASK, -1))
        for (type in NotificationType.values()) {
            composeRule.onNodeWithTag("filter-${type.key}").assertIsOff()
        }
    }

    @Test
    fun connectionLossDisablesFiltersAndManagerHasNoLauncher() {
        composeRule.runOnIdle { application.settingsStore.disconnect(application) }
        for (type in NotificationType.values()) {
            composeRule.onNodeWithTag("filter-${type.key}").assertIsNotEnabled()
        }
        composeRule.onNodeWithTag("settings-retry").performScrollTo()
            .assertIsDisplayed().performClick()
        for (type in NotificationType.values()) {
            composeRule.onNodeWithTag("filter-${type.key}").assertIsNotEnabled()
        }
        composeRule.runOnIdle { application.settingsStore.connect(application) { preferences } }
        awaitReady(0)
        for (type in NotificationType.values()) {
            composeRule.onNodeWithTag("filter-${type.key}").assertIsEnabled()
        }
        val manager = Intent(Intent.ACTION_MAIN)
            .addCategory("de.robv.android.xposed.category.MODULE_SETTINGS")
            .setPackage(application.packageName)
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            .setPackage(application.packageName)
        assertEquals(1, application.packageManager.queryIntentActivities(manager, 0).size)
        assertTrue(application.packageManager.queryIntentActivities(launcher, 0).isEmpty())
    }

    @Test
    fun everyCountVectorSurvivesShrinkingAndDrawsInBothTints() {
        val drawable = CountDrawable(application.resources)
        assertTrue(drawable.failureReason, drawable.isHealthy)
        val size = drawable.intrinsicWidth
        drawable.setBounds(0, 0, size, size)
        fun pixels(count: Int): IntArray {
            drawable.setCount(count)
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            drawable.draw(Canvas(bitmap))
            val pixels = IntArray(size * size)
            bitmap.getPixels(pixels, 0, size, 0, 0, size, size)
            bitmap.recycle()
            assertTrue(drawable.failureReason, drawable.isHealthy)
            return pixels
        }
        assertTrue(pixels(0).all { Color.alpha(it) == 0 })
        for (tint in intArrayOf(Color.WHITE, Color.BLACK)) {
            drawable.setTint(tint)
            for (count in 1..10) {
                val painted = pixels(count).filter { Color.alpha(it) > 0 }
                assertTrue("No pixels for count $count", painted.isNotEmpty())
                assertTrue("Wrong tint for count $count", painted.all { (it and 0xffffff) == (tint and 0xffffff) })
            }
            assertArrayEquals(pixels(10), pixels(99))
        }
    }

    private fun awaitReady(mask: Int) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.runOnIdle {
                val state = application.settingsStore.state()
                state.status == SettingsStore.Status.READY && state.mask == mask
            }
        }
    }

    private fun assertFilterCount() {
        assertEquals(15, NotificationType.values().size)
        composeRule.onAllNodes(SemanticsMatcher("filter row tag") { node ->
            node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("filter-") == true
        }).assertCountEquals(15)
    }
}
