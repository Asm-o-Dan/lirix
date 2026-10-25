package com.lirix.app.ui

import android.app.Activity
import android.app.Instrumentation
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.lirix.app.R
import com.lirix.app.storage.ThemePreferencesRepository
import com.lirix.app.ui.components.PlayerContextOverflow
import com.lirix.app.ui.i18n.AppLanguage
import com.lirix.app.ui.i18n.LanguageManager
import com.lirix.app.ui.i18n.LocalizedAppContent
import com.lirix.app.ui.settings.SettingsContent
import com.lirix.app.ui.theme.AppThemeType
import com.lirix.app.ui.theme.LirixTheme
import com.lirix.app.update.UpdateActivity
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import java.util.Locale

class SettingsScreenUiTest {
    private val ui = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val rules: org.junit.rules.TestRule = org.junit.rules.RuleChain
        .outerRule(ForegroundComposeHostRule()).around(ui)
    private lateinit var originalLanguage: AppLanguage
    private lateinit var originalTheme: AppThemeType

    @Before fun preservePreferences() {
        originalLanguage = LanguageManager.getCurrentLanguage(ui.activity)
        originalTheme = ThemePreferencesRepository.getInstance(ui.activity).getTheme()
        ui.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        ui.waitUntil(10_000) { ui.activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT }
    }

    @After fun restorePreferences() {
        ui.runOnIdle {
            ThemePreferencesRepository.getInstance(ui.activity).setTheme(originalTheme)
            LanguageManager.setLanguage(ui.activity, originalLanguage)
        }
    }

    private fun label(resource: Int, language: AppLanguage): String = ui.activity.createConfigurationContext(
        Configuration(ui.activity.resources.configuration).apply { setLocale(Locale(language.code)) }).getString(resource)

    @Test fun globalActionsCollapseToOneSettingsForEmptyPortraitAndLandscapeInBothLanguages() {
        val track = mutableStateOf<String?>(null)
        val landscape = mutableStateOf(false)
        var settingsClicks = 0
        ui.setContent { LocalizedAppContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                LirixTheme { Box(Modifier.width(320.dp)) {
                    PlayerContextOverflow(NowPlayingMode.LYRICS, track.value, landscape.value, false,
                        onToggleOverlay = {}, onEditLyrics = {}, onSync = {}, onTranslations = {},
                        onSaveNotes = {}, onCalibrate = {}, onTeach = null, onTrackInfo = null,
                        onSettings = { settingsClicks++ })
                } }
            }
        } }
        for (language in listOf(AppLanguage.RUSSIAN, AppLanguage.ENGLISH)) {
            ui.runOnIdle { LanguageManager.setLanguage(ui.activity, language) }
            ui.waitForIdle()
            for ((key, wide) in listOf(null to false, "song" to false, "song" to true)) {
                ui.runOnIdle { track.value = key; landscape.value = wide }
                ui.onNodeWithContentDescription(label(R.string.menu_overflow, language)).performClick()
                for (resource in listOf(R.string.menu_theme, R.string.menu_language, R.string.update_title, R.string.menu_blacklist)) {
                    ui.onNodeWithText(label(resource, language)).assertDoesNotExist()
                }
                ui.onAllNodesWithText(label(R.string.settings_title, language)).assertCountEquals(1)
                if (key == null) ui.onNodeWithText(label(R.string.menu_edit_lyrics, language)).assertDoesNotExist()
                else ui.onNodeWithText(label(R.string.menu_edit_lyrics, language)).assertExists()
                ui.onNodeWithText(label(R.string.menu_floating_overlay, language)).assertExists()
                ui.onNodeWithText(label(R.string.settings_title, language)).performScrollTo().assertIsDisplayed().performClick()
            }
        }
        ui.runOnIdle { assertEquals(6, settingsClicks) }
    }

    @Test fun realSettingsDialogsUpdateLabelsAndRoutesReturnAtTwoHundredPercentInBothLanguages() {
        var backClicks = 0
        ui.setContent { LocalizedAppContent {
            val density = LocalDensity.current
            val theme by ThemePreferencesRepository.getInstance(ui.activity).selectedTheme.collectAsState()
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                LirixTheme(themeType = theme) {
                    Box(Modifier.width(320.dp).height(560.dp)) { SettingsContent(onBack = { backClicks++ }) }
                }
            }
        } }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        for (language in listOf(AppLanguage.RUSSIAN, AppLanguage.ENGLISH)) {
            ui.runOnIdle { LanguageManager.setLanguage(ui.activity, language) }
            ui.waitForIdle()
            for (id in listOf("theme", "language", "updates", "blacklist")) {
                val row = ui.onNodeWithTag("settings-$id").performScrollTo().assertIsDisplayed()
                val bounds = row.fetchSemanticsNode().boundsInRoot
                val viewport = ui.onNodeWithTag("settings-viewport").fetchSemanticsNode().boundsInRoot
                assertTrue("Settings row $id not fully reachable: $bounds / $viewport",
                    bounds.top >= viewport.top - .5f && bounds.bottom <= viewport.bottom + .5f)
                assertTrue("Short settings target $id", bounds.height >= 48 * ui.activity.resources.displayMetrics.density)
                for (tag in listOfNotNull("settings-$id-title", "settings-$id-subtitle".takeIf { id != "blacklist" })) {
                    val node = ui.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
                    val results = mutableListOf<TextLayoutResult>()
                    assertTrue(node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results) == true)
                    assertTrue(results.isNotEmpty())
                    assertFalse("Clipped $tag: ${results.map { it.size to it.multiParagraph.height }}", results.any { it.hasVisualOverflow })
                }
            }
            ui.onNodeWithTag("settings-updates").performScrollTo()
            val installedVersion = ui.activity.packageManager.getPackageInfo(ui.activity.packageName, 0).versionName.orEmpty()
            ui.onNodeWithTag("settings-updates-subtitle", useUnmergedTree = true).assertTextContains(installedVersion, substring = true)
            val repository = ThemePreferencesRepository.getInstance(ui.activity)
            val chosenTheme = if (repository.getTheme() == AppThemeType.SPOTIFY_MOOD) AppThemeType.PASTEL_SERENITY else AppThemeType.SPOTIFY_MOOD
            ui.onNodeWithTag("settings-theme").performScrollTo().performClick()
            ui.onNode(isDialog()).assertExists()
            ui.onNodeWithText(chosenTheme.title).performScrollTo().performClick()
            ui.onNodeWithText(label(R.string.action_done, language)).performClick()
            ui.onNodeWithTag("settings-theme-subtitle", useUnmergedTree = true).assertTextEquals(chosenTheme.title)
            ui.runOnIdle { assertEquals(chosenTheme, repository.getTheme()) }
            ui.onNodeWithTag("settings-language").performScrollTo().performClick()
            val nextLanguage = if (language == AppLanguage.RUSSIAN) AppLanguage.ENGLISH else AppLanguage.RUSSIAN
            ui.onNodeWithText(label(nextLanguage.titleRes, language)).performClick()
            ui.waitForIdle()
            ui.onNodeWithTag("settings-title").assertTextEquals(label(R.string.settings_title, nextLanguage))
            ui.onNodeWithTag("settings-language-subtitle", useUnmergedTree = true).assertTextEquals(label(nextLanguage.titleRes, nextLanguage))
            ui.runOnIdle { assertEquals(nextLanguage, LanguageManager.getCurrentLanguage(ui.activity)) }
            val monitor = instrumentation.addMonitor(UpdateActivity::class.java.name,
                Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null), true)
            try {
                ui.onNodeWithTag("settings-updates").performScrollTo().performClick()
                ui.waitUntil(5_000) { monitor.hits > 0 }
            } finally { instrumentation.removeMonitor(monitor) }
            ui.onNodeWithTag("settings-blacklist").performScrollTo().assertIsDisplayed().performClick()
            ui.onNodeWithTag("settings-blacklist-screen").assertIsDisplayed()
            ui.runOnIdle { ui.activity.onBackPressedDispatcher.onBackPressed() }
            ui.onNodeWithTag("settings-blacklist").performScrollTo().assertIsDisplayed()
            ui.runOnIdle { assertEquals(0, backClicks) }
        }
        ui.onNodeWithTag("settings-back").performClick()
        ui.runOnIdle { assertEquals(1, backClicks) }
    }
}
