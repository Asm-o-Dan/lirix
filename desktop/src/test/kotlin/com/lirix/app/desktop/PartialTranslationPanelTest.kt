package com.lirix.app.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import com.lirix.app.desktop.ui.theme.DesktopFonts
import com.lirix.app.desktop.ui.theme.buildDesktopThemePalette
import com.lirix.app.translations.*
import com.lirix.app.ui.i18n.InterfaceLanguage
import com.lirix.app.ui.i18n.LirixStrings
import com.lirix.app.ui.theme.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalComposeUiApi::class)
class PartialTranslationPanelTest {
    private class MemoryDao : SongTranslationDao {
        val rows = MutableStateFlow<List<SongTranslationEntity>>(emptyList())
        override fun observe(trackKey: String, language: String) = rows.map { it.filter { row -> row.trackKey == trackKey && row.language == language } }
        override suspend fun get(trackKey: String, language: String) = rows.value.filter { it.trackKey == trackKey && it.language == language }
        override suspend fun save(entity: SongTranslationEntity) { rows.value = rows.value.filterNot { it.trackKey == entity.trackKey && it.language == entity.language && it.sourceUrl == entity.sourceUrl } + entity }
    }

    @Test fun partialCandidateRequiresConfirmationAndShowsSource() = exercise(false, false)
    @Test fun compactCrossScriptCandidateShowsIdentityBeforeConfirmation() = exercise(false, true)
    @Test fun realLatinAliasRequiresExplicitConfirmation() { exercise(false, false, "alias"); exercise(false, true, "alias") }
    @Test fun changedArtistAndVersionAreRejected() { for (compact in listOf(false, true)) { exercise(true, compact, "artist"); exercise(true, compact, "version") } }
    @Test fun changedPageIsRejectedInWideAndCompact() { exercise(true, false); exercise(true, true) }

    private fun exercise(mismatch: Boolean, compact: Boolean, scenario: String = "default") {
        val previous = LirixStrings.currentLanguage
        try {
            for (locale in InterfaceLanguage.entries) {
                LirixStrings.currentLanguage = locale
                val dao = MemoryDao()
                val isReal = scenario != "default"
                val candidate = if (isReal) TranslationCandidate("https://lyricstranslate.com/en/kipelov-ya-svoboden-english", if (scenario == "version") "Я свободен (Remastered)" else "Я свободен (Ya svoboden)", "Kipelov", "Synthetic author") else TranslationCandidate("https://example.test/a", "冬の話", "歌手", "Synthetic author")
                val document = SongTranslation(candidate.sourceUrl, if (isReal) "Я свободен" else if (mismatch) "別の曲" else candidate.title, if (scenario == "artist") "Other artist" else candidate.artist, "English title", candidate.translator,
                    listOf(TranslationBlock("", "Synthetic translated verse")), "en", if (isReal) "LyricsTranslate" else "Synthetic source", false)
                val automatic = AutomaticSongTranslations(dao, { _, _, _ -> error("Old route must not be called") },
                    preferences = MemoryTranslationLanguagePreferences("en"), searchLanguage = { _, _, _, _ ->
                        TranslationSearchResult.Candidates(listOf(candidate), listOf("LyricsTranslate"))
                    })
                var openedUrl: String? = null
                val palette = buildDesktopThemePalette(AppThemeType.PAPER_INK)
                val scene = ImageComposeScene(900, 1200, Density(1f)) {
                    CompositionLocalProvider(LocalLirixColors provides palette.tokens,
                        LocalLirixTypography provides DesktopFonts.createPrompterTypography(),
                        LocalUriHandler provides object : UriHandler { override fun openUri(uri: String) { openedUrl = uri } }) {
                        MaterialTheme(colorScheme = palette.material) { Surface(color = palette.tokens.appBackground) {
                            TranslationPanel("track", "Synthetic artist — Synthetic title", "Original local lyric", dao,
                                { url -> check(url == candidate.sourceUrl); document },
                                automatic = automatic, title = if (isReal) "Я свободен" else "Fuyu no Hanashi", artist = if (isReal) "Кипелов" else "Kashu", compact = compact)
                        } }
                    }
                }
                var nanos = 0L
                fun nodes(): List<SemanticsNode> {
                    val canvas = ImageComposeScene::class.java.getDeclaredField("scene").apply { isAccessible = true }.get(scene)
                    val owner = canvas.javaClass.getDeclaredField("mainOwner").apply { isAccessible = true }.get(canvas)
                    val layers = canvas.javaClass.getDeclaredField("layers").apply { isAccessible = true }.get(canvas) as List<*>
                    val owners = listOf(owner) + layers.map { it!!.javaClass.getDeclaredField("owner").apply { isAccessible = true }.get(it) }
                    fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
                    return owners.flatMap { flatten((it.javaClass.getMethod("getSemanticsOwner").invoke(it) as SemanticsOwner).rootSemanticsNode) }
                }
                fun text(node: SemanticsNode) = node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.joinToString("\n") { it.text }
                fun settle(expected: String) {
                    repeat(240) { index ->
                        scene.render(nanos).close(); nanos += 16_666_667; Thread.sleep(1)
                        if (index >= 30 && nodes().any { expected in text(it) }) return
                    }
                    error("Missing UI text: $expected")
                }
                fun click(label: String) {
                    val node = nodes().firstOrNull { text(it) == label && it.config.contains(SemanticsActions.OnClick) }
                        ?: error("No action: $label")
                    check(node.config[SemanticsActions.OnClick].action!!.invoke())
                }
                try {
                    val warning = if (locale == InterfaceLanguage.RU) "Не все источники ответили (LyricsTranslate)" else "Some sources did not respond (LyricsTranslate)"
                    settle(warning)
                    val instruction = if (locale == InterfaceLanguage.RU) "Есть несколько вариантов или отличается версия. Выберите нужную песню:" else "There are several matches or a different version. Choose the correct song:"
                    assertTrue(nodes().any { text(it) == instruction })
                    assertTrue(dao.rows.value.isEmpty())
                    click("${candidate.artist} — ${candidate.title} · Synthetic author")
                    if (mismatch) {
                        val stale = if (locale == InterfaceLanguage.RU) "Страница изменилась и больше не соответствует выбранному варианту. Найдите перевод снова." else "The page changed and no longer matches the selected result. Search for the translation again."
                        settle(stale)
                        assertTrue(dao.rows.value.isEmpty())
                        assertFalse(nodes().any { "Synthetic translated verse" in text(it) })
                        assertFalse(nodes().any { text(it) == "Save translation offline" || text(it) == "Сохранить перевод офлайн" })
                        continue
                    }
                    settle("Synthetic translated verse")
                    if (compact) assertTrue(nodes().any { text(it) == "${document.artist} — ${document.title}" })
                    if (scenario == "alias") {
                        val chosen = if (locale == InterfaceLanguage.RU) "В поиске: Kipelov — Я свободен (Ya svoboden)" else "Search result: Kipelov — Я свободен (Ya svoboden)"
                        val page = if (locale == InterfaceLanguage.RU) "На странице: Kipelov — Я свободен" else "On the page: Kipelov — Я свободен"
                        assertTrue(nodes().any { text(it) == chosen })
                        assertTrue(nodes().any { text(it) == page })
                    }
                    assertTrue("preview must not save partial result", dao.rows.value.isEmpty())
                    val previewLabel = if (locale == InterfaceLanguage.RU) "Предпросмотр — ещё не сохранено" else "Preview — not saved yet"
                    val confirmationLabel = if (locale == InterfaceLanguage.RU) "Это перевод нужной песни и версии записи" else "This is the correct song and recording version"
                    val checkboxLabel = if (locale == InterfaceLanguage.RU) "Подтвердить соответствие перевода текущему треку" else "Confirm that the translation matches this track"
                    val saveLabel = if (locale == InterfaceLanguage.RU) "Сохранить перевод офлайн" else "Save translation offline"
                    assertTrue(nodes().any { text(it) == previewLabel })
                    assertTrue(nodes().any { text(it) == confirmationLabel })
                    val saveBeforeConfirmation = nodes().first { text(it) == saveLabel && it.config.contains(SemanticsActions.OnClick) }
                    assertTrue("save must be disabled before explicit confirmation", saveBeforeConfirmation.config.contains(SemanticsProperties.Disabled))
                    fun hasConfirmationLabel(node: SemanticsNode): Boolean =
                        node.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(checkboxLabel) == true
                    fun subtreeHasConfirmationLabel(node: SemanticsNode): Boolean =
                        hasConfirmationLabel(node) || node.children.any(::subtreeHasConfirmationLabel)
                    val checkbox = nodes().single { node ->
                        node.config.getOrNull(SemanticsProperties.Role) == Role.Checkbox &&
                            node.config.contains(SemanticsActions.OnClick) &&
                            (generateSequence(node) { it.parent }.any(::hasConfirmationLabel) ||
                                subtreeHasConfirmationLabel(node))
                    }
                    check(checkbox.config[SemanticsActions.OnClick].action!!.invoke())
                    // Compose may schedule the checkbox state update after the input action.
                    // Wait for the actual enabled UI, not just a single rendered frame.
                    var enabledSave: SemanticsNode? = null
                    repeat(240) {
                        if (enabledSave == null) {
                            scene.render(nanos).close(); nanos += 16_666_667; Thread.sleep(1)
                            enabledSave = nodes().firstOrNull { node ->
                                text(node) == saveLabel && node.config.contains(SemanticsActions.OnClick) &&
                                    !node.config.contains(SemanticsProperties.Disabled)
                            }
                        }
                    }
                    assertNotNull("save must become enabled after explicit confirmation", enabledSave)
                    click(saveLabel)
                    repeat(240) { if (dao.rows.value.isNotEmpty()) return@repeat; scene.render(nanos).close(); nanos += 16_666_667; Thread.sleep(1) }
                    assertEquals(1, dao.rows.value.size)
                    assertEquals(document, TranslationDocuments.decode(dao.rows.value.single().documentJson))
                    if (compact || isReal) continue
                    val attribution = if (locale == InterfaceLanguage.RU) "Источник: Synthetic source · Перевод: Synthetic author" else "Source: Synthetic source · Translation: Synthetic author"
                    settle(attribution)
                    val sourceLink = if (locale == InterfaceLanguage.RU) "Открыть источник" else "Open source"
                    click(sourceLink)
                    assertEquals(candidate.sourceUrl, openedUrl)
                } finally { scene.close() }
            }
        } finally { LirixStrings.currentLanguage = previous }
    }
}
