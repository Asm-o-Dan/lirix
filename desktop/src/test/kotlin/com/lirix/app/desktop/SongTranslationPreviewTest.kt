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
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

@OptIn(ExperimentalComposeUiApi::class)
class SongTranslationPreviewTest {
    private class MemoryDao : SongTranslationDao {
        val rows = MutableStateFlow<List<SongTranslationEntity>>(emptyList())
        override fun observe(trackKey: String) = rows
        override suspend fun get(trackKey: String) = rows.value
        override suspend fun save(entity: SongTranslationEntity) { rows.value = listOf(entity) }
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
                val semantics = owner.javaClass.getMethod("getSemanticsOwner").invoke(owner) as SemanticsOwner
                fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
                return flatten(semantics.rootSemanticsNode)
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
                click("Русский"); settle("Русский перевод пока не добавлен"); capture("empty")
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
}
