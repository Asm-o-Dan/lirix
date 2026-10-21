package com.lirix.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.lirix.app.translations.*
import com.lirix.app.ui.theme.LirixTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TranslationCompactUiTest {
    @get:Rule val ui = createComposeRule()

    @Test fun compactReadingKeepsAttributionAndSettingsStateWithoutReload() {
        val author = "A very long translation author whose full name must remain accessible"
        val document = SongTranslation("https://www.amalgama-lab.com/songs/t/test/song.html",
            "Unique test title", "Unique artist", "Translated test title", author,
            listOf(TranslationBlock("Original line", "Переведённая строка")))
        val dao = FakeDao(SongTranslationEntity("ui-translation", "ru", document.sourceUrl,
            TranslationDocuments.encode(document), 1L))
        val automatic = AutomaticSongTranslations(dao, { _, _, _ -> TranslationSearchResult.Missing })
        val settings = mutableStateOf(false)
        var loads = 0
        ui.setContent {
            LirixTheme { Column(Modifier.fillMaxSize()) {
                TextButton(onClick = { settings.value = true }) { Text("Настроить перевод") }
                TranslationPanel("ui-translation", "Unique artist — Unique test title", "Original line", dao,
                    { loads++; document }, Modifier.weight(1f), automatic, "Unique test title", "Unique artist",
                    compact = true, settingsExpanded = settings.value, onDismissSettings = { settings.value = false })
            } }
        }
        ui.waitUntil(5_000) { ui.onAllNodesWithText("Переведённая строка").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("Unique artist — Unique test title").assertDoesNotExist()
        ui.onNodeWithText("Оригинал и перевод со страницы источника. Без синхронизации караоке.").assertDoesNotExist()
        ui.onNodeWithText("По ссылке").assertDoesNotExist()
        ui.onNodeWithContentDescription("Об источнике перевода: Amalgama; $author").performClick()
        ui.onNodeWithText("Перевод: $author").assertIsDisplayed()
        ui.onNodeWithText("Открыть источник").assertIsDisplayed()
        ui.onNodeWithText("Закрыть").performClick()
        ui.onNodeWithText("Настроить перевод").performClick()
        ui.onNodeWithText("Оригинал").performClick()
        ui.onNodeWithText("Готово").performClick()
        ui.onNodeWithText("Переведённая строка").assertDoesNotExist()
        ui.onNodeWithText("Original line").assertIsDisplayed()
        ui.onNodeWithText("Настроить перевод").performClick()
        ui.onNodeWithText("Оригинал").assertIsSelected()
        ui.onNodeWithText("По ссылке").performClick()
        ui.onNodeWithText("Ссылка Amalgama или LyricsTranslate").assertIsDisplayed()
        ui.onNodeWithText("Готово").performScrollTo().performClick()
        ui.onNodeWithText("Настроить перевод").performClick()
        ui.onNodeWithText("Ссылка Amalgama или LyricsTranslate").assertIsDisplayed()
        ui.runOnIdle { assertEquals(0, loads); assertEquals(1, dao.observations) }
    }

    private class FakeDao(row: SongTranslationEntity) : SongTranslationDao {
        val rows = MutableStateFlow(listOf(row))
        var observations = 0
        override fun observe(trackKey: String, language: String): Flow<List<SongTranslationEntity>> {
            observations++
            return rows
        }
        override suspend fun get(trackKey: String, language: String) = rows.value
        override suspend fun save(entity: SongTranslationEntity) { rows.value = listOf(entity) }
    }
}
