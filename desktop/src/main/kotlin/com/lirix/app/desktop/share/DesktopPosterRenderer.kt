package com.lirix.app.desktop.share

import androidx.compose.ui.graphics.toArgb
import com.lirix.app.analytics.*
import com.lirix.app.storage.TrackEntity
import com.lirix.app.ui.theme.LirixColors
import java.awt.*
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

enum class PosterFormat(val height: Int, val label: String) { STORIES(1920, "История 9:16"), SQUARE(1080, "Квадрат 1:1") }
enum class PosterSection(val label: String) { FULL("Все итоги"), TRACKS("Треки"), ARTISTS("Исполнители"), OBSESSION("На повторе"), PULSE("Ритм дня") }

/** Desktop-native export, independent of Android Canvas and Compose runtime state. */
object DesktopPosterRenderer {
    fun track(track: TrackEntity, lyric: String?, colors: LirixColors, format: PosterFormat): BufferedImage =
        Page(colors, format).apply {
            text("Lirix · Сейчас играет", 30, secondary)
            gap(36)
            cover(track.albumArtUri, if (format == PosterFormat.STORIES) 640 else 320)
            gap(32)
            text(track.title, 64, serif = true, maxLines = 2)
            text(track.artist, 36, secondary, maxLines = 2)
            gap(24)
            text(lyric.orEmpty(), 40, accent, serif = true, maxLines = 4)
        }.finish()

    fun wrapped(stats: WrappedStats, timeframe: AnalyticsTimeframe, colors: LirixColors, format: PosterFormat,
                section: PosterSection = PosterSection.FULL): BufferedImage = Page(colors, format).apply {
        text("Lirix · Итоги прослушиваний", 30, secondary)
        gap(24)
        text(if (timeframe == AnalyticsTimeframe.MONTH) "Последние 30 дней" else timeframe.labelRu, 42, accent)
        gap(24)
        when (section) {
            PosterSection.ARTISTS -> {
                text("Ваши исполнители", 66, serif = true)
                stats.topArtists.take(if (stories) 5 else 3).forEachIndexed { i, artist ->
                    gap(24); text("${i + 1}. ${artist.artist}", 44, maxLines = 2)
                    text("Запусков: ${artist.playCount}", 28, secondary)
                }
            }
            PosterSection.OBSESSION -> {
                text("На повторе", 66, serif = true); gap(32)
                stats.topObsession?.let {
                    text(it.title, 62, serif = true, maxLines = 3); text(it.artist, 36, secondary, maxLines = 2)
                    gap(24); text("Запусков за период: ${it.playCountInPeriod}", 36, accent)
                } ?: text("Пока нет трека на повторе", 40, secondary)
            }
            PosterSection.PULSE -> {
                text("Когда звучала музыка", 62, serif = true, maxLines = 2); gap(32)
                if (stats.timeOfDayDistribution.isEmpty()) text("Пока нет данных", 40, secondary)
                for ((slot, share) in stats.timeOfDayDistribution) {
                    val name = when (slot) { TimeOfDaySlot.NIGHT -> "Ночью"; TimeOfDaySlot.MORNING -> "Утром"; TimeOfDaySlot.AFTERNOON -> "Днём"; TimeOfDaySlot.EVENING -> "Вечером" }
                    text("$name · ${(share * 100).toInt()}% запусков", 42); gap(24)
                }
            }
            else -> {
                if (section == PosterSection.FULL) {
                    val minutes = stats.totalListeningTimeMs.coerceAtLeast(0) / 60_000
                    text(if (minutes < 60) "$minutes мин" else "${minutes / 60} ч ${minutes % 60} мин", if (stories) 108 else 92, serif = true)
                    text("в музыке за выбранный период", 30, secondary); gap(20)
                    text("Треков: ${stats.uniqueTracksCount} · Артистов: ${stats.uniqueArtistsCount}", 34); gap(28)
                }
                text("Любимые треки", 48, serif = true); gap(20)
                if (stats.topTracks.isEmpty()) text("Здесь появится ваша музыка", 34, secondary)
                stats.topTracks.take(if (stories) 5 else 3).forEachIndexed { i, t -> row(i + 1, t) }
            }
        }
    }.finish()

    private class Page(colors: LirixColors, format: PosterFormat) {
        val stories = format == PosterFormat.STORIES
        private val image = BufferedImage(1080, format.height, BufferedImage.TYPE_INT_RGB)
        private val g = image.createGraphics().apply { setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON); setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC) }
        private val primary = Color(colors.textPrimary.toArgb(), true)
        val secondary = Color(colors.textSecondary.toArgb(), true)
        val accent = Color(colors.accentPrimary.toArgb(), true)
        private val surface = Color(colors.surfaceCard.toArgb(), true)
        private val border = Color(colors.borderSubtle.toArgb(), true)
        private val bottom = format.height - if (stories) 240 else 112
        private var y = if (stories) 180 else 72
        init { g.color = Color(colors.appBackground.toArgb(), true); g.fillRect(0, 0, image.width, image.height) }
        fun gap(size: Int) { y += if (stories) size else size / 2 }
        private fun font(size: Int, serif: Boolean) = Font(if (serif) Font.SERIF else Font.SANS_SERIF, Font.PLAIN, size)
        private fun ellipsize(value: String, width: Int): String {
            if (g.fontMetrics.stringWidth(value) <= width) return value
            var end = value.length
            while (end > 0 && g.fontMetrics.stringWidth(value.substring(0, end) + "…") > width) end--
            return value.substring(0, end) + "…"
        }
        fun text(value: String, size: Int, color: Color = primary, serif: Boolean = false, maxLines: Int = 1) {
            if (value.isBlank()) return
            g.font = font(size, serif); g.color = color
            val lineHeight = (size * 1.3).toInt()
            val count = minOf(maxLines, (bottom - y) / lineHeight)
            if (count <= 0) return
            val words = value.replace("\n", " \n ").split(' ').filter { it.isNotEmpty() }
            var at = 0
            for (line in 0 until count) {
                if (at >= words.size) break
                val last = line == count - 1
                var content = ""
                while (at < words.size) {
                    val word = words[at]
                    if (word == "\n") { at++; break }
                    val candidate = if (content.isEmpty()) word else "$content $word"
                    if (!last && content.isNotEmpty() && g.fontMetrics.stringWidth(candidate) > 936) break
                    content = candidate; at++
                    if (!last && g.fontMetrics.stringWidth(content) > 936) break
                }
                if (last && at < words.size) content += " " + words.drop(at).joinToString(" ")
                g.drawString(ellipsize(content, 936), 72, y + g.fontMetrics.ascent)
                y += lineHeight
            }
        }
        private fun drawCover(path: String?, x: Int, top: Int, side: Int) {
            g.color = surface; g.fillRect(x, top, side, side)
            val art = path?.let { runCatching { val file = if (it.startsWith("file:")) File(java.net.URI(it)) else File(it); ImageIO.read(file) }.getOrNull() }
            if (art != null) {
                val crop = minOf(art.width, art.height); val left = (art.width - crop) / 2; val start = (art.height - crop) / 2
                g.drawImage(art, x, top, x + side, top + side, left, start, left + crop, start + crop, null)
            }
        }
        fun cover(path: String?, side: Int) { drawCover(path, (1080 - side) / 2, y, side); y += side }
        fun row(rank: Int, track: TopTrackItem) {
            val height = if (stories) 156 else 120; val side = if (stories) 112 else 88
            if (y + height > bottom) return
            drawCover(track.albumArtUri, 72, y, side)
            val x = 72 + side + 24
            fun label(value: String, offset: Int, size: Int, color: Color) {
                g.font = font(size, false); g.color = color
                g.drawString(ellipsize(value, 1080 - 72 - x), x, y + offset + g.fontMetrics.ascent)
            }
            label("$rank. ${track.title}", 0, if (stories) 40 else 34, primary)
            label(track.artist, if (stories) 48 else 42, 28, secondary)
            label("Запусков: ${track.playCount}", if (stories) 86 else 76, 25, accent)
            y += height
        }
        fun finish(): BufferedImage {
            val footer = image.height - if (stories) 180 else 72
            g.color = border; g.drawLine(72, footer - 32, 1008, footer - 32)
            g.color = secondary; g.font = font(26, false)
            g.drawString("Lirix · Музыка, которую вы выбрали", 72, footer)
            g.dispose(); return image
        }
    }
}
