package com.lirix.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.lirix.app.R
import com.lirix.app.feature.lyrics.romanization.*
import com.lirix.app.ui.components.*
import com.lirix.app.ui.theme.LirixTheme
import org.junit.Rule
import org.junit.Test

class ReadingPresentationUiTest {
    private val ui = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val rules: org.junit.rules.TestRule = org.junit.rules.RuleChain
        .outerRule(ForegroundComposeHostRule()).around(ui)
    @Test fun partialRomanizedOnlyShowsOriginalAndWarningNeverMixedReading() {
        val source = "失𯿿くす"
        ui.setContent { LirixTheme {
            RomanizedKaraokeLine(source, RomanizationDisplayMode.ROMANIZED_ONLY,
                reading = ReadingResult(source, "失xxxkusu", ReadingStatus.PARTIAL, RomanizationLanguage.JAPANESE,
                    listOf(ReadingRange(0, 3))))
        } }
        ui.onNodeWithText(source).assertIsDisplayed()
        ui.onNodeWithText(ui.activity.getString(R.string.reading_partial)).assertIsDisplayed()
        ui.onNodeWithText("失xxxkusu").assertDoesNotExist()
    }
    @Test fun realDocumentContextControlsBothTextAndKaraokeAndAmbiguityFallsBack() {
        val language = mutableStateOf(RomanizationLanguage.AUTO)
        val document = mutableStateOf("かな\n世界\n失くして\n彷徨って")
        ui.setContent { LirixTheme {
            val reading = rememberDocumentReadings("fixture", null, document.value,
                RomanizationDisplayMode.ROMANIZED_ONLY, language.value)
            Column {
                RomanizedKaraokeLine("世界", RomanizationDisplayMode.ROMANIZED_ONLY, reading = reading["世界"])
                RomanizedKaraokeLine("世界", RomanizationDisplayMode.ROMANIZED_ONLY, reading = reading["世界"])
                if (document.value.contains("失くして")) {
                    RomanizedKaraokeLine("失くして", RomanizationDisplayMode.ROMANIZED_ONLY, reading = reading["失くして"])
                    RomanizedKaraokeLine("彷徨って", RomanizationDisplayMode.ROMANIZED_ONLY, reading = reading["彷徨って"])
                }
            }
        } }
        ui.waitUntil(20_000) { ui.onAllNodesWithText("sekai").fetchSemanticsNodes().size == 2 }
        ui.onAllNodesWithText("shì jiè").assertCountEquals(0)
        ui.onNodeWithText("nakushite").assertIsDisplayed()
        ui.onNodeWithText("samayotte").assertIsDisplayed()
        ui.runOnIdle { document.value = "世界" }
        ui.waitUntil(10_000) { ui.onAllNodesWithText(ui.activity.getString(R.string.reading_needs_language)).fetchSemanticsNodes().size == 2 }
        ui.onAllNodesWithText("世界").assertCountEquals(2)
        ui.runOnIdle { language.value = RomanizationLanguage.CHINESE }
        ui.waitUntil(20_000) { ui.onAllNodesWithText("shì jiè").fetchSemanticsNodes().size == 2 }
        ui.onAllNodesWithText("sekai").assertCountEquals(0)
    }
}
