package com.lirix.app.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import com.lirix.app.desktop.ui.theme.DesktopFonts
import com.lirix.app.desktop.ui.theme.buildDesktopThemePalette
import com.lirix.app.translations.*
import com.lirix.app.ui.theme.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.atomic.AtomicInteger
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

@OptIn(ExperimentalComposeUiApi::class)
class SongTranslationPreviewTest {
    private class MemoryDao : SongTranslationDao {
        val rows = MutableStateFlow<List<SongTranslationEntity>>(emptyList())
        override fun observe(trackKey: String, language: String) = rows.map { values -> values.filter { it.trackKey == trackKey && it.language == language } }
        override suspend fun get(trackKey: String, language: String) = rows.value.filter { it.trackKey == trackKey && it.language == language }
        override suspend fun save(entity: SongTranslationEntity) { rows.value = rows.value.filterNot { it.trackKey == entity.trackKey && it.language == entity.language && it.sourceUrl == entity.sourceUrl } + entity }
    }

    @Test fun actualPanelImportConfirmationSaveAndBilingualLayout() {
        assumeTrue(System.getenv("LIRIX_RENDER_TRANSLATIONS") == "1")
        val output = File(System.getenv("LIRIX_TRANSLATION_PREVIEW_DIR") ?: "build/translation-previews").absoluteFile
        check(output.mkdirs() || output.isDirectory)
        val document = SongTranslation("https://www.amalgama-lab.com/songs/a/artist/example.html", "Synthetic example", "Example artist", "Искусственный пример", "Тестовый автор",
            listOf(TranslationBlock("[Verse]", "[Куплет]", true),
                TranslationBlock("A synthetic original line for the interface preview", "Длинная русская строка для проверки переноса текста и удобного чтения на узком экране"),
                TranslationBlock("The second source line remains paired", "Вторая строка остаётся рядом со своим оригиналом")))
        for ((width, height, theme) in listOf(Triple(360, 740, AppThemeType.PAPER_INK), Triple(900, 720, AppThemeType.DEFAULT))) {
            val dao = MemoryDao()
            val palette = buildDesktopThemePalette(theme)
            val scene = ImageComposeScene(width, height, Density(1f)) {
                CompositionLocalProvider(LocalLirixColors provides palette.tokens,
                    LocalLirixTypography provides DesktopFonts.createPrompterTypography(),
                    LocalUriHandler provides object : UriHandler { override fun openUri(uri: String) { error("Preview cannot open browser") } }) {
                    MaterialTheme(colorScheme = palette.material) {
                        Surface(color = palette.tokens.appBackground) {
                            TranslationPanel("track", "Local artist — Local title", "Original local lyrics", dao, { document })
                        }
                    }
                }
            }
            var nanos = 0L
            fun nodes(): List<SemanticsNode> {
                val field = ImageComposeScene::class.java.getDeclaredField("scene").apply { isAccessible = true }
                val canvas = field.get(scene)
                val ownerField = canvas.javaClass.getDeclaredField("mainOwner").apply { isAccessible = true }
                val owner = ownerField.get(canvas)
                val layersField = canvas.javaClass.getDeclaredField("layers").apply { isAccessible = true }
                val layers = layersField.get(canvas) as List<*>
                val owners = listOf(owner) + layers.map { layer ->
                    layer!!.javaClass.getDeclaredField("owner").apply { isAccessible = true }.get(layer)
                }
                fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
                return owners.flatMap { item ->
                    val semantics = item.javaClass.getMethod("getSemanticsOwner").invoke(item) as SemanticsOwner
                    flatten(semantics.rootSemanticsNode)
                }
            }
            fun text(node: SemanticsNode) = node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.joinToString("\n") { it.text }
            fun settle(expected: String) {
                repeat(180) { index ->
                    scene.render(nanos).close(); nanos += 16_666_667
                    Thread.sleep(1)
                    if (index >= 60 && nodes().any { text(it).contains(expected) }) return
                }
                error("Expected translation panel state: $expected")
            }
            fun click(label: String) {
                val node = nodes().firstOrNull { text(it) == label && it.config.contains(SemanticsActions.OnClick) }
                    ?: error("Missing real action $label")
                check(node.config[SemanticsActions.OnClick].action!!.invoke())
            }
            fun capture(name: String) {
                val image = scene.render(nanos); nanos += 16_666_667
                try {
                    val data = checkNotNull(image.encodeToData(EncodedImageFormat.PNG))
                    try { output.resolve("${width}x$height-$name.png").writeBytes(data.bytes) } finally { data.close() }
                } finally { image.close() }
            }
            try {
                settle("Original local lyrics")
                click("Русский"); settle("Перевод на выбранный язык пока не добавлен"); capture("empty")
                click("Добавить перевод"); settle("Перевод для:")
                val field = nodes().first { it.config.contains(SemanticsActions.SetText) }
                check(field.config[SemanticsActions.SetText].action!!.invoke(AnnotatedString(document.sourceUrl)))
                settle("Предпросмотр"); click("Предпросмотр"); settle("Предпросмотр — ещё не сохранено"); capture("preview")
                assertTrue(dao.rows.value.isEmpty())
                val checkbox = nodes().first { "Подтвердить соответствие перевода текущему треку" in it.config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() } }
                check(checkbox.config[SemanticsActions.OnClick].action!!.invoke())
                settle("Сохранить перевод офлайн"); click("Сохранить перевод офлайн")
                settle("Без синхронизации караоке."); capture("saved-together")
                assertEquals(document, TranslationDocuments.decode(dao.rows.value.single().documentJson))
                click("Оригинал"); settle("Original local lyrics")
                click("Русский"); settle("Длинная русская строка"); capture("saved-russian")
            } finally { scene.close() }
        }
    }

    @Test fun optionalUserProvidedSourceProbeDoesNotPrintLyrics() {
        val path = System.getenv("LIRIX_AMALGAMA_HTML")
        assumeTrue(path != null)
        // This inspection copy was decoded during HTTP fetch and saved as UTF-8.
        val doc = AmalgamaTranslationProvider.parse(File(path!!).readBytes(), "https://www.amalgama-lab.com/songs/f/falling_in_reverse/joseph.html", "UTF-8")
        assertEquals("Joseph", doc.title)
        assertEquals("JaneTheDestroyer", doc.translator)
        assertEquals("Джозеф", doc.translatedTitle)
        assertTrue(doc.blocks.size >= 60)
        println("User-provided source parsed: ${doc.blocks.size} paired rows; metadata verified. Lyrics not printed.")
    }

    @Test fun automaticTranslationAppearsAndPersistsWithoutClicksOrUrlEntry() {
        assumeTrue(System.getenv("LIRIX_RENDER_TRANSLATIONS") == "1")
        val output = File(System.getenv("LIRIX_TRANSLATION_PREVIEW_DIR") ?: "build/translation-previews").absoluteFile
        check(output.mkdirs() || output.isDirectory)
        val document = SongTranslation("https://www.amalgama-lab.com/songs/a/artist/example.html", "Example", "Artist", "Пример", "Тестовый автор",
            listOf(TranslationBlock("Synthetic original line", "Автоматически найденный русский перевод без ввода ссылки")))
        for ((width, height) in listOf(360 to 740, 900 to 720)) {
            val dao = MemoryDao()
            val release = CompletableDeferred<Unit>()
            val calls = AtomicInteger()
            val automatic = AutomaticSongTranslations(dao, { _, _, _ -> error("legacy search") }, searchLanguage = { _, _, language, _ ->
                calls.incrementAndGet(); release.await()
                TranslationSearchResult.Found(if (language == "ru") document else document.copy(language = language, sourceName = "LyricsTranslate", aligned = false,
                    blocks = listOf(TranslationBlock("Synthetic original", "Traducción sintética en español"))))
            })
            val palette = buildDesktopThemePalette(AppThemeType.PAPER_INK)
            val scene = ImageComposeScene(width, height, Density(1f)) {
                CompositionLocalProvider(LocalLirixColors provides palette.tokens,
                    LocalLirixTypography provides DesktopFonts.createPrompterTypography(),
                    LocalUriHandler provides object : UriHandler { override fun openUri(uri: String) { error("No browser in preview") } }) {
                    MaterialTheme(colorScheme = palette.material) { Surface(color = palette.tokens.appBackground) {
                        TranslationPanel("key", "Artist — Example", "Synthetic original line", dao,
                            { error("Automatic flow must not request a manual URL") }, automatic = automatic, title = "Example", artist = "Artist")
                    } }
                }
            }
            var nanos = 0L
            fun nodes(): List<SemanticsNode> {
                val field = ImageComposeScene::class.java.getDeclaredField("scene").apply { isAccessible = true }
                val canvas = field.get(scene)
                val ownerField = canvas.javaClass.getDeclaredField("mainOwner").apply { isAccessible = true }
                val owner = ownerField.get(canvas)
                val layersField = canvas.javaClass.getDeclaredField("layers").apply { isAccessible = true }
                val layers = layersField.get(canvas) as List<*>
                val owners = listOf(owner) + layers.map { layer ->
                    layer!!.javaClass.getDeclaredField("owner").apply { isAccessible = true }.get(layer)
                }
                fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
                return owners.flatMap { item ->
                    val semantics = item.javaClass.getMethod("getSemanticsOwner").invoke(item) as SemanticsOwner
                    flatten(semantics.rootSemanticsNode)
                }
            }
            fun settle(expected: String) {
                repeat(240) { index ->
                    scene.render(nanos).close(); nanos += 16_666_667; Thread.sleep(1)
                    if (index >= 60 && nodes().any { node -> node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.any { it.text.contains(expected) } }) return
                }
                error("Automatic state missing: $expected")
            }
            fun capture(label: String) {
                val image = scene.render(nanos); nanos += 16_666_667
                try { val data = checkNotNull(image.encodeToData(EncodedImageFormat.PNG)); try {
                    output.resolve("${width}x$height-$label.png").writeBytes(data.bytes)
                } finally { data.close() } } finally { image.close() }
            }
            try {
                settle("Ищем перевод"); capture("automatic-searching")
                assertTrue(dao.rows.value.isEmpty())
                settle("Synthetic original line")
                release.complete(Unit)
                settle("Автоматически найденный русский перевод"); capture("automatic-ready")
                assertEquals(1, calls.get())
                assertEquals(document, TranslationDocuments.decode(dao.rows.value.single().documentJson))
                assertFalse(nodes().any { it.config.contains(SemanticsActions.SetText) })
                val heading = nodes().first { node -> node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.any { it.text == "Artist — Example" } }
                assertTrue("Controls must leave room for the lyrics", heading.boundsInRoot.top < 160f)
                assertFalse(nodes().any { node -> node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.any { it.text == "Español" } })
                val languageButton = nodes().first { node -> "Выбрать язык перевода" in node.config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() } }
                check(languageButton.config[SemanticsActions.OnClick].action!!.invoke())
                settle("Español"); capture("language-menu")
                val spanish = nodes().first { node -> node.config.contains(SemanticsActions.OnClick) &&
                    node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.any { it.text == "Español" } }
                check(spanish.config[SemanticsActions.OnClick].action!!.invoke())
                settle("Traducción sintética en español"); capture("automatic-spanish")
                assertEquals("es", automatic.language.value)
                assertEquals(setOf("ru", "es"), dao.rows.value.map { it.language }.toSet())
                assertFalse(nodes().any { node -> node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.any { it.text.contains("Автоматически найденный русский перевод") } })
            } finally { release.complete(Unit); scene.close() }
        }
    }
}
