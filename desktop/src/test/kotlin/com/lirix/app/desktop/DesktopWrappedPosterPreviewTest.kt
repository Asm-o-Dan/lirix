package com.lirix.app.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.material3.Surface
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import com.lirix.app.analytics.*
import com.lirix.app.desktop.ui.DesktopWrappedPosterContent
import com.lirix.app.desktop.ui.theme.buildDesktopThemePalette
import com.lirix.app.ui.theme.*
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

@OptIn(ExperimentalComposeUiApi::class)
class DesktopWrappedPosterPreviewTest {
    @Test fun actualPreviewTracksFormatAndSectionBeforeExport() {
        assumeTrue(System.getenv("LIRIX_RENDER_PREVIEWS") == "1")
        val stats = WrappedStatsEngine.calculateStats(emptyList(), emptyList())
        val output = File("build/wrapped-ux-previews").apply { mkdirs() }
        for (theme in listOf(AppThemeType.PAPER_INK, AppThemeType.DEFAULT)) {
            val palette = buildDesktopThemePalette(theme)
            val scene = ImageComposeScene(900, 720, Density(1f)) {
                CompositionLocalProvider(LocalLirixColors provides palette.tokens) {
                    MaterialTheme(colorScheme = palette.material) {
                        Surface(color = palette.tokens.appBackground) { Column(Modifier.fillMaxSize()) {
                            DesktopWrappedPosterContent(stats, AnalyticsTimeframe.MONTH, palette.tokens, false, {}, {}, {})
                        } }
                    }
                }
            }
            var nanos = 0L
            fun nodes(): List<SemanticsNode> {
                val canvas = ImageComposeScene::class.java.getDeclaredField("scene").apply { isAccessible = true }.get(scene)
                val main = canvas.javaClass.getDeclaredField("mainOwner").apply { isAccessible = true }.get(canvas)
                val layers = canvas.javaClass.getDeclaredField("layers").apply { isAccessible = true }.get(canvas) as List<*>
                val owners = listOf(main) + layers.map { layer -> layer!!.javaClass.getDeclaredField("owner").apply { isAccessible = true }.get(layer) }
                fun flatten(n: SemanticsNode): List<SemanticsNode> = listOf(n) + n.children.flatMap(::flatten)
                return owners.flatMap { owner -> flatten((owner.javaClass.getMethod("getSemanticsOwner").invoke(owner) as SemanticsOwner).rootSemanticsNode) }
            }
            fun text(n: SemanticsNode) = n.config.getOrElse(SemanticsProperties.Text) { emptyList() }.joinToString("") { it.text }
            fun settle(predicate: () -> Boolean) {
                repeat(350) { i ->
                    scene.render(nanos).close(); nanos += 16_666_667; Thread.sleep(2)
                    if (i > 30 && predicate()) return
                }
                error("Poster preview did not settle")
            }
            fun click(label: String) {
                val node = nodes().first { text(it) == label && it.config.contains(SemanticsActions.OnClick) }
                check(node.config[SemanticsActions.OnClick].action!!.invoke())
            }
            fun preview(label: String) = nodes().any { label in it.config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() } }
            try {
                settle { preview("Предпросмотр итогов: История 9:16, Все итоги") }
                click("Формат: История 9:16")
                settle { nodes().any { text(it) == "Квадрат 1:1" } }
                click("Квадрат 1:1")
                settle { preview("Предпросмотр итогов: Квадрат 1:1, Все итоги") }
                click("Содержимое: Все итоги")
                settle { nodes().any { text(it) == "Ритм дня" } }
                click("Ритм дня")
                settle { preview("Предпросмотр итогов: Квадрат 1:1, Ритм дня") }
                val save = nodes().first { text(it) == "Сохранить PNG" && it.config.contains(SemanticsActions.OnClick) }
                assertFalse(save.config.contains(SemanticsProperties.Disabled))
                val image = scene.render(nanos)
                try { val data = image.encodeToData(EncodedImageFormat.PNG)!!
                    try { output.resolve("${theme.id}-square-pulse.png").writeBytes(data.bytes) } finally { data.close() }
                } finally { image.close() }
            } finally { scene.close() }
        }
    }
}
