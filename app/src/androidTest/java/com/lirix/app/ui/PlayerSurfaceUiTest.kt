package com.lirix.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.lirix.app.R
import com.lirix.app.ui.components.*
import com.lirix.app.feature.lyrics.*
import com.lirix.app.ui.theme.LirixTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PlayerSurfaceUiTest {
    private val ui = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val rules: org.junit.rules.TestRule = org.junit.rules.RuleChain
        .outerRule(ForegroundComposeHostRule()).around(ui)

    @Test fun markerDragIncludesTouchSlopAndCancellationNeverCommits() {
        val initial = KaraokeStudioEngine.createSession("gesture-test", "", "[00:01.000] First\n[00:01.500] Next", 5000)
            .copy(cursorMs = 1000, spanMs = 2000)
        val state = mutableStateOf(initial)
        ui.setContent { LirixTheme {
            Box(Modifier.width(320.dp)) {
                KaraokeStudioTimeline(state.value, onCursor = { state.value = state.value.copy(cursorMs = it) },
                    onSelect = { state.value = state.value.copy(selectedIndex = it) }, onCommit = { index, time ->
                        state.value = (KaraokeStudioEngine.reduce(state.value, KaraokeStudioEdit.SetTime(index, time)) as KaraokeStudioResult.Accepted).state
                    }, onInteraction = {})
            }
        } }
        val timeline = ui.onNodeWithContentDescription(ui.activity.getString(R.string.studio_timeline_description))
        timeline.performTouchInput {
            down(center)
            moveTo(center + Offset(width / 8f, 0f), delayMillis = 500)
            up()
        }
        ui.runOnIdle {
            assertEquals(1250L, state.value.lines[0].timeMs)
            assertEquals(1, state.value.undo.size)
            state.value = initial
        }
        timeline.performTouchInput {
            down(center)
            moveTo(center + Offset(width / 8f, 0f), delayMillis = 500)
            cancel()
        }
        ui.runOnIdle {
            assertEquals(initial.lines, state.value.lines)
            assertTrue(state.value.undo.isEmpty())
        }
    }

    @Test fun largeFontModeLabelsFitAndRemainSelectableOnNarrowPhone() {
        val selected = mutableStateOf(NowPlayingMode.KARAOKE)
        ui.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                LirixTheme { Box(Modifier.width(320.dp)) { PlayerModeSelector(selected.value, { selected.value = it }) } }
            }
        }
        val selector = ui.onNodeWithTag("player-mode-selector").fetchSemanticsNode().boundsInRoot
        for (mode in NowPlayingMode.entries) {
            val label = ui.activity.getString(mode.labelRes)
            val node = ui.onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode()
            val results = mutableListOf<TextLayoutResult>()
            assertTrue(node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results) == true)
            assertTrue(results.isNotEmpty())
            assertFalse("Clipped label: $label; bounds=${node.boundsInRoot}; layouts=${results.map { result ->
                "size=${result.size}, paragraph=${result.multiParagraph.width}x${result.multiParagraph.height}, " +
                    "overflowWidth=${result.didOverflowWidth}, overflowHeight=${result.didOverflowHeight}, " +
                    "lines=${result.lineCount}, constraints=${result.layoutInput.constraints}, style=${result.layoutInput.style}, " +
                    "lineEdges=${(0 until result.lineCount).map { result.getLineLeft(it) to result.getLineRight(it) }}"
            }}", results.any { it.hasVisualOverflow })
            assertTrue(node.boundsInRoot.top >= selector.top && node.boundsInRoot.bottom <= selector.bottom)
            ui.onNodeWithText(label).performClick().assertIsSelected()
        }
    }

    @Test fun contextSwitchDismissesMenuAndOnlyOffersSupportedTrackActions() {
        val mode = mutableStateOf(NowPlayingMode.LYRICS)
        ui.setContent { LirixTheme {
            PlayerContextOverflow(mode.value, "test-song", false, false,
                onToggleOverlay = {}, onEditLyrics = {}, onSync = {}, onTranslations = {}, onSaveNotes = {},
                onCalibrate = {}, onTeach = null, onTrackInfo = {}, onTheme = {}, onBlacklist = {})
        } }
        fun open() = ui.onNodeWithContentDescription(ui.activity.getString(R.string.menu_overflow)).performClick()
        fun has(resource: Int) = ui.onNodeWithText(ui.activity.getString(resource)).assertExists()
        fun missing(resource: Int) = ui.onNodeWithText(ui.activity.getString(resource)).assertDoesNotExist()
        open()
        has(R.string.menu_translations); has(R.string.menu_edit_lyrics); has(R.string.menu_sync_studio)
        missing(R.string.menu_calibrate_timing)
        ui.runOnIdle { mode.value = NowPlayingMode.KARAOKE }
        missing(R.string.menu_theme)
        open(); has(R.string.menu_calibrate_timing); has(R.string.menu_sync_studio); missing(R.string.menu_translations)
        ui.runOnIdle { mode.value = NowPlayingMode.CHORDS }
        missing(R.string.menu_theme)
        open(); has(R.string.menu_track_info); missing(R.string.menu_translations); missing(R.string.menu_calibrate_timing); missing(R.string.menu_edit_lyrics)
        ui.runOnIdle { mode.value = NowPlayingMode.NOTES }
        open(); has(R.string.menu_save_notes); missing(R.string.menu_sync_studio); missing(R.string.menu_edit_lyrics)
    }
}
