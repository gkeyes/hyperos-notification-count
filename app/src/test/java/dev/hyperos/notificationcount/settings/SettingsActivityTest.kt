package dev.hyperos.notificationcount.settings

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import dev.hyperos.notificationcount.R
import dev.hyperos.notificationcount.core.NotificationType
import java.util.concurrent.Executor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

/** Exercises the real Miuix screen hosted by the manifest's settings activity. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37], application = SettingsActivityTest.TestApplication::class)
class SettingsActivityTest {
    class TestApplication : ModuleApplication() {
        private var settings = directStore()

        override fun onCreate() {
            // The test application never registers libxposed's real process service.
        }

        override fun getSettingsStore(): SettingsStore = settings

        fun replaceSettingsStore(): SettingsStore {
            settings = directStore()
            return settings
        }

        private fun directStore() = SettingsStore(Executor { it.run() }, Executor { it.run() })
    }

    @get:Rule
    val composeRule = createAndroidComposeRule<SettingsActivity>()

    private lateinit var application: TestApplication
    private lateinit var persisted: SharedPreferences

    @Before
    fun setUp() {
        composeRule.runOnIdle {
            application = RuntimeEnvironment.getApplication() as TestApplication
            persisted = application.getSharedPreferences("settings-test", Context.MODE_PRIVATE)
            assertTrue(persisted.edit().clear().commit())
        }
    }

    @Test
    fun allFifteenFiltersHaveTheirOwnLabelsTagsAndOffDefaults() {
        assertFilterCount()
        for (type in NotificationType.values()) {
            val (title, description) = expectedLabels(type)
            filter(type)
                .assertIsOff()
                .assertIsNotEnabled()
                .assertHasNoClickAction()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
                .assertContentDescriptionEquals(
                    application.getString(title) + "。" + application.getString(description),
                )
        }
        composeRule.onNodeWithTag("settings-reset").assertIsNotEnabled()
        composeRule.onNodeWithTag("settings-retry").assertIsEnabled()
        assertStatus(R.string.settings_waiting)
        assertSelected(0)

        connectPersisted()

        assertMaskAndEnabled(0, enabled = true)
        composeRule.onNodeWithTag("settings-retry").assertDoesNotExist()
        assertStatus(R.string.settings_ready)
    }

    @Test
    fun togglingEveryFilterPersistsReopensAndResetsTogether() {
        connectPersisted()
        var mask = 0
        for (type in NotificationType.values()) {
            filter(type).performScrollTo().assertIsDisplayed().performClick()
            mask = mask or type.bit
            filter(type).assertIsOn()
            assertEquals(type.key, mask, composeRule.runOnIdle { persistedMask() })
            assertEquals(type.key, mask, composeRule.runOnIdle { application.getSettingsStore().state().mask })
        }
        assertEquals(FilterPreferences.KNOWN_MASK, mask)
        assertSelected(15)

        // Reopen against a fresh store, so a remembered activity/store mask cannot satisfy the test.
        val previousActivity = composeRule.activity
        composeRule.runOnIdle {
            application.replaceSettingsStore().connect(Any()) { persisted }
        }
        composeRule.activityRule.scenario.recreate()
        assertNotSame(previousActivity, composeRule.activity)
        assertMaskAndEnabled(FilterPreferences.KNOWN_MASK, enabled = true)

        composeRule.onNodeWithTag("settings-reset")
            .performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()

        assertMaskAndEnabled(0, enabled = true)
        assertEquals(0, composeRule.runOnIdle { persistedMask() })
        composeRule.onNodeWithTag("settings-reset").assertIsNotEnabled()
        assertSelected(0)
    }

    @Test
    fun failedSaveRestoresSwitchesDisablesEditsAndRetryKeepsTheConfirmedMask() {
        val previous = NotificationType.ONGOING_EVENT.bit
        val remote = SettingsTestDoubles.OptimisticPreferences(previous)
        remote.queueCommitResults(false, false)
        composeRule.runOnIdle {
            application.getSettingsStore().connect(Any()) { remote.preferences }
        }

        filter(NotificationType.MEDIA).performScrollTo().assertIsDisplayed().performClick()

        assertMaskAndEnabled(previous, enabled = false)
        composeRule.runOnIdle {
            assertEquals(listOf(previous or NotificationType.MEDIA.bit, previous), remote.committedMasks)
            assertEquals(previous, application.getSettingsStore().state().mask)
            assertEquals(SettingsStore.Status.SAVE_FAILED, application.getSettingsStore().state().status)
        }
        composeRule.onNodeWithTag("settings-reset").assertIsNotEnabled()
        assertStatus(R.string.settings_save_failed)
        composeRule.onNodeWithTag("settings-retry")
            .performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()

        assertMaskAndEnabled(previous, enabled = true)
        composeRule.runOnIdle {
            assertEquals(previous, remote.persistedMask())
            assertEquals(listOf(previous or NotificationType.MEDIA.bit, previous, previous), remote.committedMasks)
        }
        composeRule.onNodeWithTag("settings-retry").assertDoesNotExist()
        assertStatus(R.string.settings_ready)
    }

    @Test
    fun stoppedScreenStopsObservingAndRendersCurrentStateWhenStartedAgain() {
        connectPersisted()
        filter(NotificationType.FOCUS).assertIsOff()
        composeRule.runOnIdle { assertEquals(1, observerCount()) }

        val scenario = composeRule.activityRule.scenario
        scenario.moveToState(Lifecycle.State.CREATED)
        // A stopped Compose owner is not queried for UI assertions. Check removal while stopped,
        // then verify that onStart observes the latest store state after the owner resumes.
        scenario.onActivity {
            assertEquals(0, observerCount())
            application.getSettingsStore().setExcluded(NotificationType.FOCUS, true)
            assertEquals(NotificationType.FOCUS.bit, application.getSettingsStore().state().mask)
        }
        scenario.moveToState(Lifecycle.State.RESUMED)

        filter(NotificationType.FOCUS).performScrollTo().assertIsDisplayed().assertIsOn()
        assertMaskAndEnabled(NotificationType.FOCUS.bit, enabled = true)
        composeRule.runOnIdle { assertEquals(1, observerCount()) }
    }

    @Test
    fun manifestResolvesTheManagerSettingsCategoryWithoutALauncherEntry() {
        composeRule.runOnIdle {
            val settings = Intent(Intent.ACTION_MAIN)
                .addCategory("de.robv.android.xposed.category.MODULE_SETTINGS")
                .setPackage(application.packageName)
            val matches = application.packageManager.queryIntentActivities(settings, 0)
            assertEquals(1, matches.size)
            assertEquals(SettingsActivity::class.java.name, matches.single().activityInfo.name)
            assertTrue(matches.single().activityInfo.exported)
            val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setPackage(application.packageName)
            assertTrue(application.packageManager.queryIntentActivities(launcher, 0).isEmpty())
        }
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-night")
    fun darkModeAndLargerFontStillAllowTheLastFilterAndResetToBeReached() {
        composeRule.runOnIdle { RuntimeEnvironment.setFontScale(1.3f) }
        composeRule.activityRule.scenario.recreate()
        val reopenedActivity = composeRule.activity
        composeRule.runOnIdle {
            val configuration = reopenedActivity.resources.configuration
            assertEquals(1.3f, configuration.fontScale, 0.01f)
            assertEquals(Configuration.UI_MODE_NIGHT_YES, configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK)
        }
        connectPersisted()
        assertFilterCount()
        composeRule.onNodeWithTag("settings-scroll").assert(hasScrollAction())
        filter(NotificationType.FOLDED).assertIsNotDisplayed()

        filter(NotificationType.FOLDED).performScrollTo().assertIsDisplayed().performClick()

        filter(NotificationType.FOLDED).assertIsOn()
        assertEquals(NotificationType.FOLDED.bit, composeRule.runOnIdle { persistedMask() })
        composeRule.onNodeWithTag("settings-reset")
            .performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()
        assertMaskAndEnabled(0, enabled = true)
        assertEquals(0, composeRule.runOnIdle { persistedMask() })
        filter(NotificationType.FOCUS).performScrollTo().assertIsDisplayed().assertIsOff()
    }

    private fun connectPersisted() {
        composeRule.runOnIdle { application.getSettingsStore().connect(Any()) { persisted } }
    }

    private fun filter(type: NotificationType): SemanticsNodeInteraction =
        composeRule.onNodeWithTag("filter-${type.key}")

    private fun assertFilterCount() {
        assertEquals(15, NotificationType.values().size)
        composeRule.onAllNodes(SemanticsMatcher("filter row tag") { node ->
            node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("filter-") == true
        }).assertCountEquals(15)
    }

    private fun assertMaskAndEnabled(mask: Int, enabled: Boolean) {
        assertFilterCount()
        for (type in NotificationType.values()) {
            val node = filter(type)
            if (mask and type.bit != 0) node.assertIsOn() else node.assertIsOff()
            if (enabled) node.assertIsEnabled().assertHasClickAction()
            else node.assertIsNotEnabled().assertHasNoClickAction()
        }
        assertSelected(Integer.bitCount(mask))
    }

    private fun assertStatus(resource: Int) {
        composeRule.onNodeWithTag("settings-status").assertTextEquals(application.getString(resource))
    }

    private fun assertSelected(count: Int) {
        composeRule.onNodeWithTag("settings-selected")
            .assertTextEquals(application.getString(R.string.settings_selected, count))
    }

    private fun persistedMask() = persisted.getInt(FilterPreferences.EXCLUDED_MASK, -1)

    private fun observerCount(): Int =
        ReflectionHelpers.getField<Set<*>>(application.getSettingsStore(), "observers").size

    // Independent expected resource mapping: the production filterLabels function is not the oracle.
    private fun expectedLabels(type: NotificationType): Pair<Int, Int> = when (type) {
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
}
