package com.lirix.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.ComponentActivity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.lirix.app.R
import com.lirix.app.ui.components.*
import com.lirix.app.ui.theme.LirixTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PlayerContextUiTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()

    // These component fixtures inherit the Activity context; use its actual resources
    // so persisted app language or system locale cannot invalidate semantic lookups.
    private fun label(resource: Int): String = ui.activity.getString(resource)

    @Test fun portraitPopupStaysBesideTopRightTriggerInsideViewport() {
        assertPopupGeometry(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, Configuration.ORIENTATION_PORTRAIT)
    }

    @Test fun landscapePopupStaysBesideTopRightTriggerInsideViewport() {
        assertPopupGeometry(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, Configuration.ORIENTATION_LANDSCAPE)
    }

    private fun assertPopupGeometry(requested: Int, expected: Int) {
        ui.activity.requestedOrientation = requested
        ui.waitUntil(10_000) { ui.activity.resources.configuration.orientation == expected }
        ui.setContent { LirixTheme {
            Box(Modifier.fillMaxSize().testTag("viewport"), contentAlignment = Alignment.TopEnd) {
                PlayerContextOverflow(NowPlayingMode.LYRICS, "geometry", expected == Configuration.ORIENTATION_LANDSCAPE,
                    false, onToggleOverlay = {}, onEditLyrics = {}, onSync = {}, onTranslations = {},
                    onSaveNotes = {}, onCalibrate = {}, onTeach = {}, onTrackInfo = {}, onSettings = {})
            }
        } }
        val trigger = ui.onNodeWithContentDescription(label(R.string.menu_overflow)).fetchSemanticsNode()
        ui.onNodeWithContentDescription(label(R.string.menu_overflow)).performClick()
        val popup = ui.onNode(isPopup()).fetchSemanticsNode()
        val root = ui.onNodeWithTag("viewport").fetchSemanticsNode()
        // Screen coordinates are essential: a popup owns a different Compose root/window.
        val anchorPosition = trigger.positionOnScreen
        val popupPosition = popup.positionOnScreen
        val rootPosition = root.positionOnScreen
        val tolerance = 3f
        assertTrue(popupPosition.x >= rootPosition.x - tolerance)
        assertTrue(popupPosition.y >= rootPosition.y - tolerance)
        assertTrue(popupPosition.x + popup.size.width <= rootPosition.x + root.size.width + tolerance)
        assertTrue(popupPosition.y + popup.size.height <= rootPosition.y + root.size.height + tolerance)
        assertTrue(popupPosition.x + popup.size.width >= anchorPosition.x)
        assertTrue(popupPosition.y <= anchorPosition.y + trigger.size.height + tolerance)
        assertTrue(popupPosition.y + popup.size.height >= anchorPosition.y)
    }

    @Test fun realContextChangesDismissAndExposeOnlyReachableCommands() {
        val mode = mutableStateOf(NowPlayingMode.LYRICS)
        val track = mutableStateOf<String?>("ui-test-track")
        val landscape = mutableStateOf(false)
        var translations = 0
        var saved = 0
        ui.setContent {
            LirixTheme {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopEnd) {
                    PlayerContextOverflow(mode.value, track.value, landscape.value, false,
                        onToggleOverlay = {}, onEditLyrics = {}, onSync = {}, onTranslations = { translations++ },
                        onSaveNotes = { saved++ }, onCalibrate = {}, onTeach = {}, onTrackInfo = {}, onSettings = {})
                }
            }
        }
        ui.onNodeWithContentDescription(label(R.string.menu_overflow)).performClick()
        ui.onNodeWithText(label(R.string.menu_translations)).assertIsDisplayed().performClick()
        ui.runOnIdle { assertEquals(1, translations) }
        ui.onNodeWithText(label(R.string.menu_translations)).assertDoesNotExist()
        ui.onNodeWithContentDescription(label(R.string.menu_overflow)).performClick()
        ui.runOnIdle { mode.value = NowPlayingMode.KARAOKE }
        ui.onNodeWithText(label(R.string.menu_edit_lyrics)).assertDoesNotExist()
        ui.onNodeWithContentDescription(label(R.string.menu_overflow)).performClick()
        ui.onNodeWithText(label(R.string.menu_calibrate_timing)).assertIsDisplayed()
        ui.onNodeWithText(label(R.string.menu_translations)).assertDoesNotExist()
        ui.runOnIdle { mode.value = NowPlayingMode.CHORDS }
        ui.onNodeWithContentDescription(label(R.string.menu_overflow)).performClick()
        ui.onNodeWithText(label(R.string.menu_teach_mode)).assertIsDisplayed()
        ui.onNodeWithText(label(R.string.menu_calibrate_timing)).assertDoesNotExist()
        ui.onNodeWithText(label(R.string.menu_edit_lyrics)).assertDoesNotExist()
        ui.runOnIdle { mode.value = NowPlayingMode.NOTES }
        ui.onNodeWithContentDescription(label(R.string.menu_overflow)).performClick()
        ui.onNodeWithText(label(R.string.menu_save_notes)).performClick()
        ui.runOnIdle { assertEquals(1, saved); track.value = null }
        ui.onNodeWithContentDescription(label(R.string.menu_overflow)).performClick()
        ui.onNodeWithText(label(R.string.menu_save_notes)).assertDoesNotExist()
        ui.onNodeWithText(label(R.string.menu_track_info)).assertDoesNotExist()
        ui.onNodeWithText(label(R.string.menu_floating_overlay)).assertIsDisplayed()
        ui.onNodeWithText(label(R.string.player_empty_connect_pc)).assertDoesNotExist()
        ui.runOnIdle { track.value = "different-track" }
        ui.onNodeWithText(label(R.string.menu_floating_overlay)).assertDoesNotExist()
        ui.onNodeWithContentDescription(label(R.string.menu_overflow)).performClick()
        ui.runOnIdle { landscape.value = true }
        ui.onNodeWithText(label(R.string.menu_floating_overlay)).assertDoesNotExist()
    }

    @Test fun transportHasOneConnectAndCenteredPlayWithDisabledSkipSlots() {
        var connects = 0
        var shares = 0
        ui.setContent {
            LirixTheme { Box(Modifier.width(320.dp)) {
                PlayerTransportRow(false, false, { connects++ }, {}, {}, {}, { shares++ }, false, false)
            } }
        }
        // Canonical transport still exposes fixed Russian descriptions. This fixture
        // checks geometry/callbacks, not transport localization.
        ui.onAllNodesWithContentDescription("Подключить устройство (ПК)").assertCountEquals(1)
        ui.onNodeWithContentDescription("Подключить устройство (ПК)").performClick()
        ui.onNodeWithContentDescription("Поделиться карточкой").performClick()
        ui.onNodeWithContentDescription("Предыдущий").assertIsNotEnabled()
        ui.onNodeWithContentDescription("Следующий").assertIsNotEnabled()
        val play = ui.onNodeWithContentDescription("Играть").getUnclippedBoundsInRoot()
        val row = ui.onNodeWithTag("player-transport").getUnclippedBoundsInRoot()
        val rowCenterX = (row.left.value + row.right.value) / 2f
        val playCenterX = (play.left.value + play.right.value) / 2f
        assertEquals(rowCenterX, playCenterX, 1f)
        ui.runOnIdle { assertEquals(1, connects); assertEquals(1, shares) }
    }

    @Test fun rtlLargeTypePopupIsScrollableAndKeepsCallbacksReachable() {
        var settings = 0
        ui.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl,
                LocalDensity provides Density(LocalDensity.current.density, 1.8f)) {
                LirixTheme { Box(Modifier.fillMaxSize().testTag("viewport"), contentAlignment = Alignment.TopEnd) {
                    PlayerContextOverflow(NowPlayingMode.LYRICS, "rtl", true, false,
                        onToggleOverlay = {}, onEditLyrics = {}, onSync = {}, onTranslations = {},
                        onSaveNotes = {}, onCalibrate = {}, onTeach = {}, onTrackInfo = {}, onSettings = { settings++ })
                } }
            }
        }
        ui.onNodeWithContentDescription(label(R.string.menu_overflow)).performClick()
        ui.onNodeWithText(label(R.string.settings_title)).performScrollTo().assertIsDisplayed().performClick()
        ui.runOnIdle { assertEquals(1, settings) }
        ui.onNodeWithText(label(R.string.menu_translations)).assertDoesNotExist()
    }
}
