package com.lirix.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.lirix.app.R
import com.lirix.app.ui.i18n.*
import org.junit.Rule
import org.junit.Test

/** Real Compose regression: language changes must invalidate resources without keying away UI state. */
class LocaleSwitchUiTest {
    private val ui = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val rules: org.junit.rules.TestRule = org.junit.rules.RuleChain
        .outerRule(ForegroundComposeHostRule()).around(ui)
    @Test fun resourcesChangeAndRememberedStateSurvives() {
        ui.runOnUiThread { LanguageManager.setLanguage(ui.activity, AppLanguage.RUSSIAN) }
        ui.setContent {
            LocalizedAppContent {
                val context = LocalContext.current
                var count by rememberSaveable { mutableIntStateOf(0) }
                Column {
                    Text(stringResource(R.string.tab_now_playing))
                    Text(context.getString(R.string.menu_language))
                    Text("count=$count")
                    Button(onClick = { count++ }) { Text("increment") }
                    Button(onClick = { LanguageManager.setLanguage(context, AppLanguage.ENGLISH) }) { Text("English") }
                }
            }
        }
        ui.onNodeWithText("increment").performClick()
        ui.onNodeWithText("English").performClick()
        ui.onNodeWithText("Now Playing").assertIsDisplayed()
        ui.onNodeWithText("Language").assertIsDisplayed()
        ui.onNodeWithText("count=1").assertIsDisplayed()
        ui.runOnUiThread { LanguageManager.setLanguage(ui.activity, AppLanguage.SYSTEM) }
    }
    @Test fun actualLyricsEditorKeepsUnsavedContentAcrossFrameworkLocaleChange() {
        ui.runOnUiThread { LanguageManager.setLanguage(ui.activity, AppLanguage.RUSSIAN) }
        ui.setContent {
            LocalizedAppContent {
                com.lirix.app.ui.theme.LirixTheme {
                    com.lirix.app.ui.components.EditLyricsDialog(
                        trackTitle = "Test track", artist = "Test artist", initialLyrics = "Original draft",
                        onDismissRequest = {}, onSaveLyrics = {}
                    )
                }
            }
        }
        ui.onNode(hasSetTextAction()).performTextReplacement("Unsaved note 日本語")
        ui.runOnUiThread { LanguageManager.setLanguage(ui.activity, AppLanguage.ENGLISH) }
        ui.onNode(hasSetTextAction()).assertTextContains("Unsaved note 日本語")
        // Assert an actual editor resource changes, not just a preference write.
        ui.onNodeWithText("Done").assertIsDisplayed()
        ui.runOnUiThread { LanguageManager.setLanguage(ui.activity, AppLanguage.SYSTEM) }
    }

}
