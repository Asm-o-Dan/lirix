package com.lirix.app.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.semantics.SemanticsProperties
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

/** Real shared TranslationPanel semantics; synthetic content, no provider/network or screenshot dependency. */
@OptIn(ExperimentalComposeUiApi::class)
class UnalignedTranslationPanelTest {
    private class MemoryDao(document: SongTranslation) : SongTranslationDao {
        val row = SongTranslationEntity("track", document.language, document.sourceUrl, TranslationDocuments.encode(document), 1)
        val rows = MutableStateFlow(listOf(row))
        override fun observe(trackKey: String, language: String) = rows.map { values ->
            values.filter { it.trackKey == trackKey && it.language == language }
        }
        override suspend fun get(trackKey: String, language: String) = rows.value.filter { it.trackKey == trackKey && it.language == language }
        override suspend fun save(entity: SongTranslationEntity) { error("These read-only rendering tests must not save") }
    }

    private fun runPanel(locale: InterfaceLanguage, width: Int, trackOriginal: String, documentOriginal: String,
                         aligned: Boolean = false, compact: Boolean = false, reading: Boolean = false, assertions: PanelProbe.() -> Unit) {
        val previous = LirixStrings.currentLanguage
        LirixStrings.currentLanguage = locale
        val document = SongTranslation("https://example.test/page#section", "Synthetic song", "Synthetic artist", "Synthetic title", "Synthetic translator",
            listOf(TranslationBlock(documentOriginal, "Translation paragraph one"), TranslationBlock("", "Translation paragraph two")),
            language = "ru", sourceName = "Synthetic provider", aligned = aligned)
        val dao = MemoryDao(document)
        val automatic = if (compact) AutomaticSongTranslations(dao, { _, _, _ -> error("No network search") }) else null
        val palette = buildDesktopThemePalette(AppThemeType.PAPER_INK)
        val scene = ImageComposeScene(width, 1200, Density(1f)) {
            CompositionLocalProvider(LocalLirixColors provides palette.tokens,
                LocalLirixTypography provides DesktopFonts.createPrompterTypography(),
                LocalUriHandler provides object : UriHandler { override fun openUri(uri: String) = error("No browser calls") }) {
                MaterialTheme(colorScheme = palette.material) {
                    Surface(color = palette.tokens.appBackground) {
                        TranslationPanel("track", "Synthetic artist — Synthetic song", trackOriginal, dao,
                            { error("Saved document must not require a network request") }, automatic = automatic, compact = compact,
                            originalLineContent = if (reading) { line -> Text("reading:$line") } else null)
                    }
                }
            }
        }
        try { PanelProbe(scene).assertions() } finally { scene.close(); LirixStrings.currentLanguage = previous }
    }

    private class PanelProbe(private val scene: ImageComposeScene) {
        private var nanos = 0L
        private fun nodes(): List<SemanticsNode> {
            val field = ImageComposeScene::class.java.getDeclaredField("scene").apply { isAccessible = true }
            val canvas = field.get(scene)
            val owner = canvas.javaClass.getDeclaredField("mainOwner").apply { isAccessible = true }.get(canvas)
            val layers = canvas.javaClass.getDeclaredField("layers").apply { isAccessible = true }.get(canvas) as List<*>
            val owners = listOf(owner) + layers.map { layer -> layer!!.javaClass.getDeclaredField("owner").apply { isAccessible = true }.get(layer) }
            fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
            return owners.flatMap { obj -> flatten((obj.javaClass.getMethod("getSemanticsOwner").invoke(obj) as SemanticsOwner).rootSemanticsNode) }
        }
        private fun text(node: SemanticsNode) = node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.joinToString("\n") { it.text }
        fun allTexts(): List<String> = nodes().map(::text).filter { it.isNotBlank() }
        fun settle(expected: String) {
            repeat(240) { index ->
                scene.render(nanos).close(); nanos += 16_666_667; Thread.sleep(1)
                if (index >= 30 && allTexts().any { expected in it }) return
            }
            error("Expected '$expected' in ${allTexts()}")
        }
        fun click(label: String) {
            val node = nodes().firstOrNull { text(it) == label && it.config.contains(SemanticsActions.OnClick) }
                ?: error("No action '$label' in ${allTexts()}")
            check(node.config[SemanticsActions.OnClick].action!!.invoke())
        }
    }

    @Test fun unalignedUsesIndependentOriginalAndTranslationInBothLocalesAndWidths() {
        for (locale in InterfaceLanguage.entries) for (width in listOf(420, 900)) {
            val together = if (locale == InterfaceLanguage.RU) "Вместе" else "Together"
            val original = if (locale == InterfaceLanguage.RU) "Оригинал" else "Original"
            runPanel(locale, width, "Track original line\nNext original line", "Provider original must not override") {
                settle("Track original line"); click(together); settle("Translation paragraph two")
                val togetherTexts = allTexts()
                assertEquals("original should be rendered once ($locale/$width)", 1, togetherTexts.count { "Track original line" in it })
                assertTrue(togetherTexts.any { "Translation paragraph one" in it })
                assertTrue(togetherTexts.any { "Translation paragraph two" in it })
                assertFalse(togetherTexts.any { "Provider original must not override" in it })
                assertFalse(togetherTexts.any { it.contains("[Нет строки оригинала]") || it.contains("[Original line missing]") })
                click("Русский"); settle("Translation paragraph one")
                assertFalse("translation-only must not show original", allTexts().any { "Track original line" in it })
                click(original); settle("Track original line")
                assertFalse("original-only must not show translation", allTexts().any { "Translation paragraph one" in it })
            }
        }
    }

    @Test fun customReadingLayerRendersOriginalInBothModes() {
        runPanel(InterfaceLanguage.RU, 900, "Track original line", "Provider original", reading = true) {
            settle("reading:Track original line")
            click("Вместе"); settle("Translation paragraph one")
            assertTrue(allTexts().any { "reading:Track original line" in it })
        }
        runPanel(InterfaceLanguage.RU, 900, "Track original line", "Aligned source original", aligned = true, reading = true) {
            settle("reading:Track original line")
            click("Вместе"); settle("Translation paragraph one")
            assertTrue(allTexts().any { "reading:Aligned source original" in it })
        }
    }

    @Test fun alignedDocumentsKeepTheirPairedOriginalInsteadOfTrackOriginal() {
        runPanel(InterfaceLanguage.RU, 900, "Independent track original", "Aligned source original", aligned = true) {
            settle("Independent track original"); click("Вместе"); settle("Translation paragraph one")
            assertTrue(allTexts().any { "Aligned source original" in it })
            assertFalse(allTexts().any { "Independent track original" in it })
        }
    }

    @Test fun compactUnalignedShowsIndependentExplanationAndUnavailableOriginal() {
        for (locale in InterfaceLanguage.entries) {
            val unavailable = if (locale == InterfaceLanguage.RU) "Оригинал пока не сохранён" else "Original lyrics not saved yet"
            val explanation = if (locale == InterfaceLanguage.RU) "Соответствие строк не подтверждено" else "Line matching is unconfirmed"
            runPanel(locale, 420, "", "", compact = true) {
                settle("Translation paragraph two")
                assertEquals(1, allTexts().count { unavailable in it })
                assertTrue(allTexts().any { explanation in it })
            }
        }
    }

    @Test fun unavailableOriginalAndProviderFallbackStayHonest() {
        for (locale in InterfaceLanguage.entries) {
            val unavailable = if (locale == InterfaceLanguage.RU) "Оригинал пока не сохранён" else "Original lyrics not saved yet"
            val together = if (locale == InterfaceLanguage.RU) "Вместе" else "Together"
            runPanel(locale, 420, "", "") {
                settle(unavailable); click(together); settle("Translation paragraph two")
                assertEquals("one explicit unavailable message", 1, allTexts().count { unavailable in it })
            }
            runPanel(locale, 900, "", "Fallback provider original") {
                settle("Fallback provider original"); click(together); settle("Translation paragraph two")
                assertEquals(1, allTexts().count { "Fallback provider original" in it })
                assertFalse(allTexts().any { unavailable in it })
            }
        }
    }
}
