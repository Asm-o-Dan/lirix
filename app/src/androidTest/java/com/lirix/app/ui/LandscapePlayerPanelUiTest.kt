package com.lirix.app.ui

import android.graphics.Bitmap
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.lirix.app.R
import com.lirix.app.ui.components.*
import com.lirix.app.ui.theme.LirixTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class LandscapePlayerPanelUiTest {
    private val ui = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val rules: org.junit.rules.TestRule = org.junit.rules.RuleChain
        .outerRule(ForegroundComposeHostRule()).around(ui)

    @Test fun shortDeckShowsEveryControlWithoutScrolling() = verifyShortDeck(1f)
    @Test fun shortDeckAtTwoHundredPercentKeepsControlsAndFullTrackDetails() = verifyShortDeck(2f)

    private fun verifyShortDeck(fontScale: Float) {
        ui.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        ui.waitUntil(10_000) { ui.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
        val title = "Gold — Imagine Dragons — a long title that must remain available in full"
        val artist = "Imagine Dragons"
        val selected = mutableStateOf(NowPlayingMode.KARAOKE)
        val playing = mutableStateOf(false)
        val position = mutableStateOf(45_000L)
        val calls = mutableListOf<String>()
        ui.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                LirixTheme {
                    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).testTag("safe-viewport")) {
                        // Production component receives a strict 248 dp viewport inside actual safe insets.
                        LandscapePlayerPanel(title, artist, null, "Spotify", playing.value, false, selected.value,
                            onSelectMode = { selected.value = it }, onBack = { calls += "back" },
                            onConnect = { calls += "connect" }, onPrevious = { calls += "previous" },
                            onTogglePlaying = { playing.value = !playing.value; calls += "play" },
                            onNext = { calls += "next" }, onShare = { calls += "share" },
                            overflow = {
                                PlayerContextOverflow(selected.value, "fixture", true, false,
                                    onToggleOverlay = {}, onEditLyrics = {}, onSync = {}, onTranslations = {},
                                    onSaveNotes = {}, onCalibrate = {}, onTeach = null, onTrackInfo = null,
                                    onTheme = {}, onBlacklist = {})
                            },
                            seek = { LandscapePlayerSeekBar(position.value, 180_000L, { position.value = it }) },
                            modifier = Modifier.width(280.dp).height(248.dp))
                    }
                }
            }
        }
        ui.waitForIdle()
        captureFixture(fontScale)
        val panel = ui.onNodeWithTag("landscape-player-panel").fetchSemanticsNode().boundsInRoot
        val safe = ui.onNodeWithTag("safe-viewport").fetchSemanticsNode().boundsInRoot
        assertTrue("Panel outside safe viewport", panel.left >= safe.left && panel.right <= safe.right && panel.top >= safe.top && panel.bottom <= safe.bottom)
        val minTarget = 48 * ui.activity.resources.displayMetrics.density
        fun fits(tag: String, interactive: Boolean = false) {
            val node = ui.onNodeWithTag(tag).assertIsDisplayed().fetchSemanticsNode()
            val bounds = node.boundsInRoot
            assertTrue("$tag clipped by panel: $bounds / $panel", bounds.left >= panel.left && bounds.right <= panel.right && bounds.top >= panel.top && bounds.bottom <= panel.bottom)
            if (interactive) {
                assertTrue("$tag target width ${bounds.width}, expected $minTarget; bounds=$bounds; panel=$panel; safe=$safe", bounds.width + .5f >= minTarget)
                assertTrue("$tag target height ${bounds.height}, expected $minTarget; bounds=$bounds; panel=$panel; safe=$safe", bounds.height + .5f >= minTarget)
            }
        }
        listOf("back", "connect", "previous", "play", "next", "share", "mode", "seek").forEach { fits("landscape-$it", true) }
        fits("landscape-seek-row"); fits("landscape-metadata")
        val withinPanel = hasTestTag("landscape-player-panel") or hasAnyAncestor(hasTestTag("landscape-player-panel"))
        ui.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange) and withinPanel,
            useUnmergedTree = true).assertCountEquals(0)
        ui.onNodeWithContentDescription(ui.activity.getString(R.string.text_size_decrease)).assertDoesNotExist()
        ui.onNodeWithContentDescription(ui.activity.getString(R.string.text_size_increase)).assertDoesNotExist()
        ui.onNodeWithText("−").assertDoesNotExist(); ui.onNodeWithText("+").assertDoesNotExist()
        for (tag in listOf("landscape-time", "landscape-duration", "landscape-mode-label")) {
            val results = mutableListOf<TextLayoutResult>()
            val node = ui.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
            assertTrue(node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results) == true)
            assertTrue(results.isNotEmpty())
            assertFalse("Clipped text $tag; bounds=${node.boundsInRoot}; layouts=${results.map { result ->
                "size=${result.size}, paragraph=${result.multiParagraph.width}x${result.multiParagraph.height}, " +
                    "overflowWidth=${result.didOverflowWidth}, overflowHeight=${result.didOverflowHeight}, " +
                    "constraints=${result.layoutInput.constraints}, lines=${result.lineCount}"
            }}", results.any { it.hasVisualOverflow })
        }
        for (tag in listOf("back", "connect", "previous", "play", "next", "share")) ui.onNodeWithTag("landscape-$tag").performClick()
        ui.runOnIdle {
            assertEquals(listOf("back", "connect", "previous", "play", "next", "share"), calls)
            assertTrue(playing.value)
        }
        ui.onNodeWithTag("landscape-seek").performSemanticsAction(SemanticsActions.SetProgress) { it(90_000f) }
        ui.runOnIdle { assertEquals(90_000L, position.value) }
        // Verify both vertical edges of the advertised 48 dp seek target really accept touch.
        ui.onNodeWithTag("landscape-seek").performTouchInput { click(Offset(width * .25f, 1f)) }
        ui.runOnIdle { assertTrue("Top edge of seek target did not seek", position.value < 70_000L) }
        ui.onNodeWithTag("landscape-seek").performTouchInput { click(Offset(width * .75f, height - 1f)) }
        ui.runOnIdle { assertTrue("Bottom edge of seek target did not seek", position.value > 110_000L) }
        ui.onNodeWithTag("landscape-mode").performClick()
        ui.onNodeWithText(ui.activity.getString(NowPlayingMode.CHORDS.labelRes)).performClick()
        ui.runOnIdle { assertEquals(NowPlayingMode.CHORDS, selected.value) }
        ui.onNodeWithTag("landscape-metadata").performClick()
        ui.onNodeWithTag("landscape-full-title").assertIsDisplayed().assertTextEquals(title)
        ui.onNodeWithTag("landscape-full-artist").performScrollTo().assertIsDisplayed().assertTextEquals(artist)
        ui.onNodeWithText(ui.activity.getString(R.string.action_close)).performClick()
        ui.waitForIdle()
        captureFixture(fontScale)
    }

    private fun captureFixture(fontScale: Float) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = File(instrumentation.targetContext.getExternalFilesDir(null), "qa/landscape-player-${fontScale.toInt()}.png")
        file.parentFile?.mkdirs()
        val screenshot = ui.onNodeWithTag("landscape-player-panel").captureToImage().asAndroidBitmap()
        file.outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        screenshot.recycle()
    }
}
