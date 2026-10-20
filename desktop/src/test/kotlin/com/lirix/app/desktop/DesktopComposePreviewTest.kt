package com.lirix.app.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.Density
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.lirix.app.desktop.hud.HudSettings
import com.lirix.app.desktop.library.DesktopLibraryRepository
import com.lirix.app.desktop.lyrics.DesktopLyricsService
import com.lirix.app.desktop.lyrics.DesktopLyricsState
import com.lirix.app.desktop.ui.DesktopWorkspace
import com.lirix.app.desktop.ui.theme.DesktopFonts
import com.lirix.app.desktop.ui.theme.buildDesktopThemePalette
import com.lirix.app.feature.music.MusicTrackParser
import com.lirix.app.ingestion.LivePlaybackSnapshot
import com.lirix.app.platform.MediaIngressProvider
import com.lirix.app.storage.AppDatabase
import com.lirix.app.storage.ListeningSessionEntity
import com.lirix.app.storage.TrackEntity
import com.lirix.app.ui.player.LrcLine
import com.lirix.app.ui.theme.AppThemeType
import com.lirix.app.ui.theme.LocalLirixColors
import com.lirix.app.ui.theme.LocalLirixTypography
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Optional artifact generator, not a screenshot assertion or native acceptance test.
 * Enable with LIRIX_RENDER_PREVIEWS=1; add LIRIX_PREVIEW_MATRIX=1 for six themes/two sizes.
 * No Main, real media ingress, native dialogs, hotkey registration or Preferences writes.
 */
@OptIn(ExperimentalComposeUiApi::class)
class DesktopComposePreviewTest {
    @Test fun renderActualWorkspaceArtifacts() = runBlocking {
        assumeTrue("Optional visual artifact generator", System.getenv("LIRIX_RENDER_PREVIEWS") == "1")
        val output = File(System.getenv("LIRIX_PREVIEW_DIR") ?: "build/compose-previews").absoluteFile
        check(output.mkdirs() || output.isDirectory)
        val fixture = Fixture()
        try {
            renderWorkspace(fixture, output, AppThemeType.DEFAULT, 1280, 820, null,
                DesktopLyricsState(), "idle", navigate = false)
            fixture.populate()
            if (System.getenv("LIRIX_PREVIEW_FOCUSED") == "1") {
                renderWorkspace(fixture, output, AppThemeType.PAPER_INK, 980, 680, fixture.playback,
                    fixture.timedLyrics, "karaoke", navigate = true)
            }
            if (System.getenv("LIRIX_PREVIEW_MATRIX") == "1") {
                for (theme in AppThemeType.entries) for ((width, height) in listOf(1280 to 820, 980 to 680)) {
                    renderWorkspace(fixture, output, theme, width, height, fixture.playback,
                        fixture.timedLyrics, "karaoke", navigate = true)
                    renderWorkspace(fixture, output, theme, width, height, fixture.playback,
                        fixture.plainLyrics, "no-timing", navigate = false)
                }
            }
            output.resolve("README.txt").writeText(
                "Actual DesktopWorkspace rendered by Compose 1.7.3 ImageComposeScene CPU Skia.\n" +
                    "Temporary Room, deterministic sample metadata, paused playback, real desktop font/theme locals.\n" +
                    "Library/Wrapped/settings invoke actual sidebar semantics OnClick actions; content headings are verified before capture.\n" +
                    "Captures settle at least 90 frames (1.5 seconds virtual), up to 180 frames, with stable semantics geometry and explicit page readiness.\n" +
                    "No native windows, WGC capture, hotkeys, audio, tray, file chooser, drag or multi-monitor acceptance.\n" +
                    "Deck reads existing Windows sync-offset preference; no preference is written.\n" +
                    "PNG existence is not a visual quality assertion.\n"
            )
            println("Compose preview artifacts: ${output.absolutePath}")
        } finally { fixture.close() }
    }

    private class Fixture {
        private val directory = Files.createTempDirectory("lirix-compose-preview-").toFile()
        private val database = Room.databaseBuilder<AppDatabase>(name = directory.resolve("preview.db").absolutePath)
            .setDriver(BundledSQLiteDriver()).build()
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val repository = DesktopLibraryRepository(database, scope)
        val service = DesktopLyricsService(scope, repository, cacheDirectory = directory.resolve("cache"),
            automaticLookup = { _, _, _, _, _ -> error("Preview must never start a provider lookup") })
        private val title = "Длинное название песни — ночные огни большого города"
        private val artist = "Исполнитель с длинным именем и приглашённый музыкант"
        private val album = "Альбом для проверки переноса текста"
        private val key = MusicTrackParser.computeTrackKey(title, artist, album)
        val playback = LivePlaybackSnapshot("preview.fixture", title, artist, album,
            isPlaying = false, basePositionMs = 3000, lastPositionUpdateTimeMs = 0, durationMs = 215000,
            timestamp = 1_760_000_000_000)
        val timedLyrics = DesktopLyricsState(trackKey = key, displayTitle = title, displayArtist = artist,
            lrcLines = listOf(LrcLine(0, "Первая строка: за окном загораются огни"),
                LrcLine(3000, "Очень длинная строка для проверки реального переноса текста караоке на минимальной ширине окна"),
                LrcLine(7000, "Третья строка остаётся следующей")),
            plainLyrics = "Первая строка\nОчень длинная строка\nТретья строка", source = "Сохранено вами",
            hasLyrics = true, syncedLyricsLrc = "[00:00.00] Первая строка\n[00:03.00] Очень длинная строка\n[00:07.00] Третья строка")
        val plainLyrics = timedLyrics.copy(lrcLines = emptyList(), syncedLyricsLrc = null,
            plainLyrics = "Обычный текст без временных меток.\nВторая строка сохранённого текста.")

        suspend fun populate() {
            val now = System.currentTimeMillis()
            repeat(8) { index ->
                val track = if (index == 0) TrackEntity(key, title, artist, album, "preview.fixture",
                    playCount = 5, totalDurationMs = 195000, lastPlayedAt = now, firstPlayedAt = now - 86400000,
                    userNotes = "Проверить аккорды и сохранить любимую строку", isFavorite = true,
                    plainLyrics = timedLyrics.plainLyrics, syncedLyrics = timedLyrics.syncedLyricsLrc)
                else TrackEntity("preview-$index", "Композиция $index — строка для проверки списка",
                    "Исполнитель ${index % 3}", "Альбом $index", "preview.fixture", playCount = index + 1,
                    totalDurationMs = 120000L + index * 10000, firstPlayedAt = now - 86400000,
                    lastPlayedAt = now - index * 60000, isFavorite = index % 2 == 0,
                    plainLyrics = if (index % 2 == 0) "Сохранённый текст композиции $index" else null)
                database.musicDao().insertOrUpdateTrack(track)
                database.musicDao().insertSession(ListeningSessionEntity(trackKey = track.trackKey,
                    sourcePackage = "preview.fixture", startTimeMs = now - (index + 1) * 3600000,
                    endTimeMs = now - (index + 1) * 3600000 + track.totalDurationMs,
                    durationMs = track.totalDurationMs, isCompleted = true))
            }
            withTimeout(10000) {
                repository.tracks.first { it.size == 8 }
                repository.sessions.first { it.size == 8 }
                repository.achievements.first { it.isNotEmpty() }
            }
        }

        suspend fun close() {
            scope.coroutineContext[Job]!!.cancelAndJoin()
            database.close()
            directory.deleteRecursively()
        }
    }

    private fun renderWorkspace(fixture: Fixture, output: File, theme: AppThemeType,
        width: Int, height: Int, playback: LivePlaybackSnapshot?, lyrics: DesktopLyricsState,
        stateName: String, navigate: Boolean) {
        var playbackActions = 0
        val ingress = object : MediaIngressProvider {
            override val currentPlayback = MutableStateFlow(playback)
            override fun playPause() { playbackActions++ }
            override fun seekTo(positionMs: Long) = Unit
            override fun skipNext() = Unit
            override fun skipPrevious() = Unit
        }
        val palette = buildDesktopThemePalette(theme)
        val colors = palette.tokens
        val typography = DesktopFonts.createPrompterTypography()
        val scene = ImageComposeScene(width, height, Density(1f)) {
            CompositionLocalProvider(LocalLirixColors provides colors, LocalLirixTypography provides typography) {
                MaterialTheme(colorScheme = palette.material) {
                    DesktopWorkspace(fixture.repository, fixture.service, playback, ingress, lyrics,
                        theme, {}, false, {}, HudSettings(), {}, {}, {}, null, false, {}, emptyList())
                }
            }
        }
        var nanos = 0L
        fun frame() { val image = scene.render(nanos); nanos += 16_666_667; image.close() }
        fun nodes(): List<SemanticsNode> {
            // Compose 1.7.3 ImageComposeScene.roots throws NotImplementedError. Inspect only
            // this detached scene's actual semantics owner; fail on version drift, never fake a page.
            val sceneField = ImageComposeScene::class.java.getDeclaredField("scene").apply { isAccessible = true }
            val canvasScene = sceneField.get(scene)
            val ownerField = canvasScene.javaClass.getDeclaredField("mainOwner").apply { isAccessible = true }
            val owner = ownerField.get(canvasScene)
            val semantics = owner.javaClass.getMethod("getSemanticsOwner").invoke(owner) as SemanticsOwner
            fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
            return flatten(semantics.rootSemanticsNode)
        }
        fun text(node: SemanticsNode): String = node.config.getOrElse(SemanticsProperties.Text) { emptyList() }
            .joinToString("\n") { it.text }
        fun contentHas(value: String) = nodes().any { it.boundsInRoot.left >= 172f && text(it).contains(value) }
        fun settle(ready: () -> Boolean = { true }) {
            var previous = ""
            var stable = 0
            repeat(180) { index ->
                frame()
                // Give real asynchronous effects (e.g. Wrapped's Default dispatcher) a bounded
                // scheduling opportunity while advancing the actual Compose animation clock.
                Thread.sleep(1)
                val signature = nodes().joinToString("|") { "${text(it)}:${it.boundsInRoot}" }
                stable = if (signature == previous) stable + 1 else 0
                previous = signature
                if (index >= 89 && stable >= 12 && ready()) return
            }
            check(ready() && stable >= 12) { "Detached scene did not reach ready/stable state within 180 frames" }
        }
        fun capture(label: String, heading: String? = null, readyText: String? = null) {
            settle { (heading == null || contentHas(heading)) && (readyText == null || contentHas(readyText)) }
            if (heading != null) check(contentHas(heading)) { "Wrong destination for $label: expected $heading" }
            if (playback != null) {
                val transport = nodes().firstOrNull { "Воспроизведение" in it.config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() } && it.config.contains(SemanticsActions.OnClick) }
                    ?: error("Persistent playback action missing on $label")
                val before = playbackActions
                check(transport.config[SemanticsActions.OnClick].action!!.invoke())
                check(playbackActions == before + 1) { "Playback action did not reach ingress on $label" }
            }
            val image = scene.render(nanos); nanos += 16_666_667
            try {
                val data = checkNotNull(image.encodeToData(EncodedImageFormat.PNG))
                try { output.resolve("${theme.id}-${width}x$height-$label.png").writeBytes(data.bytes) }
                finally { data.close() }
            } finally { image.close() }
            output.resolve("${theme.id}-${width}x$height-$label.semantics.txt").writeText(
                nodes().joinToString("\n") { "${it.boundsInRoot} ${text(it)}" })
        }
        fun navigateTo(label: String) {
            val button = nodes().firstOrNull { it.boundsInRoot.left < 172f && text(it) == label }
                ?: error("Sidebar destination not present: $label")
            val click = button.config[SemanticsActions.OnClick].action
                ?: error("Sidebar has no actual OnClick: $label")
            check(click()) { "Sidebar OnClick rejected: $label" }
        }
        try {
            capture(stateName)
            if (navigate) {
                // Invoke the actual detached composition's accessibility action, not a copied
                // page switch or a native Windows control. Pointer routing is outside this audit.
                navigateTo("Медиатека"); capture("library", "Медиатека")
                navigateTo("Итоги"); capture("wrapped", "Итоги прослушиваний", "Любимые треки")
                navigateTo("Настройки"); capture("settings", "Настройки", "Чёрный список")
            }
        } finally { scene.close() }
    }
}
