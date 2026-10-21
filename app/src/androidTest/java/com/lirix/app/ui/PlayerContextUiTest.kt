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
import com.lirix.app.ui.components.*
import com.lirix.app.ui.theme.LirixTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PlayerContextUiTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()

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
                    false, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
            }
        } }
        val trigger = ui.onNodeWithContentDescription("Меню").fetchSemanticsNode()
        ui.onNodeWithContentDescription("Меню").performClick()
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
                        {}, {}, {}, { translations++ }, { saved++ }, {}, {}, {}, {}, {})
                }
            }
        }
        ui.onNodeWithContentDescription("Меню").performClick()
        ui.onNodeWithText("Перевод и язык").assertIsDisplayed().performClick()
        ui.runOnIdle { assertEquals(1, translations) }
        ui.onNodeWithText("Перевод и язык").assertDoesNotExist()
        ui.onNodeWithContentDescription("Меню").performClick()
        ui.runOnIdle { mode.value = NowPlayingMode.KARAOKE }
        ui.onNodeWithText("Редактировать текст песни").assertDoesNotExist()
        ui.onNodeWithContentDescription("Меню").performClick()
        ui.onNodeWithText("Калибровка задержки").assertIsDisplayed()
        ui.onNodeWithText("Перевод и язык").assertDoesNotExist()
        ui.runOnIdle { mode.value = NowPlayingMode.CHORDS }
        ui.onNodeWithContentDescription("Меню").performClick()
        ui.onNodeWithText("Найти текст или аккорды (Teach)").assertIsDisplayed()
        ui.onNodeWithText("Калибровка задержки").assertDoesNotExist()
        ui.onNodeWithText("Редактировать текст песни").assertDoesNotExist()
        ui.runOnIdle { mode.value = NowPlayingMode.NOTES }
        ui.onNodeWithContentDescription("Меню").performClick()
        ui.onNodeWithText("Сохранить заметки").performClick()
        ui.runOnIdle { assertEquals(1, saved); track.value = null }
        ui.onNodeWithContentDescription("Меню").performClick()
        ui.onNodeWithText("Сохранить заметки").assertDoesNotExist()
        ui.onNodeWithText("О треке и источниках").assertDoesNotExist()
        ui.onNodeWithText("Плавающий оверлей").assertIsDisplayed()
        ui.onNodeWithText("Подключить ПК (Lirix Connect)").assertDoesNotExist()
        ui.runOnIdle { track.value = "different-track" }
        ui.onNodeWithText("Плавающий оверлей").assertDoesNotExist()
        ui.onNodeWithContentDescription("Меню").performClick()
        ui.runOnIdle { landscape.value = true }
        ui.onNodeWithText("Плавающий оверлей").assertDoesNotExist()
    }

    @Test fun transportHasOneConnectAndCenteredPlayWithDisabledSkipSlots() {
        var connects = 0
        var shares = 0
        ui.setContent {
            LirixTheme { Box(Modifier.width(320.dp)) {
                PlayerTransportRow(false, false, { connects++ }, {}, {}, {}, { shares++ }, false, false)
            } }
        }
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
        var blacklist = 0
        ui.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl,
                LocalDensity provides Density(LocalDensity.current.density, 1.8f)) {
                LirixTheme { Box(Modifier.fillMaxSize().testTag("viewport"), contentAlignment = Alignment.TopEnd) {
                    PlayerContextOverflow(NowPlayingMode.LYRICS, "rtl", true, false,
                        {}, {}, {}, {}, {}, {}, {}, {}, {}, { blacklist++ })
                } }
            }
        }
        ui.onNodeWithContentDescription("Меню").performClick()
        ui.onNodeWithText("Чёрный список фонотеки").performScrollTo().assertIsDisplayed().performClick()
        ui.runOnIdle { assertEquals(1, blacklist) }
        ui.onNodeWithText("Перевод и язык").assertDoesNotExist()
    }
}
