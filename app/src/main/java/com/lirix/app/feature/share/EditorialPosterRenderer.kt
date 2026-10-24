package com.lirix.app.feature.share

import android.graphics.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import com.lirix.app.analytics.AnalyticsTimeframe
import com.lirix.app.analytics.WrappedStats
import com.lirix.app.storage.TrackEntity

/** Theme-aware export path. Layout and drawing receive the same immutable palette. */
internal object EditorialPosterRenderer {
    fun wrapped(
        stats: WrappedStats, timeframe: AnalyticsTimeframe, format: ShareCardFormat,
        theme: PosterThemeSnapshot, artwork: Map<String, Bitmap?>,
        section: WrappedShareSection, labels: PosterTextSnapshot
    ): Bitmap {
        val page = Page(format, theme, labels)
        page.text(labels.text("Lirix · Итоги прослушиваний"), 30f, theme.secondary)
        page.gap(32f)
        page.text(periodLabel(timeframe, labels), 42f, theme.accent)
        page.gap(24f)
        val minutes = stats.totalListeningTimeMs.coerceAtLeast(0) / 60000
        val time = if (minutes >= 60) labels.text("{0} ч {1} мин", minutes / 60, minutes % 60) else labels.text("{0} мин", minutes)
        when (section) {
            WrappedShareSection.ARTISTS -> {
                page.text(labels.text("Ваши исполнители"), 72f, serif = true)
                page.gap(40f)
                stats.topArtists.take(5).forEachIndexed { index, artist ->
                    page.text("${index + 1}. ${artist.artist}", 46f, maxLines = 2)
                    page.text(labels.quantity(artist.playCount, labels.text("запуск"), labels.text("запуска"), labels.text("запусков")), 30f, theme.secondary)
                    page.gap(28f)
                }
            }
            WrappedShareSection.OBSESSION -> {
                page.text(labels.text("На повторе"), 72f, serif = true)
                page.gap(32f)
                stats.topObsession?.let {
                    page.text(it.title, 64f, serif = true, maxLines = 3)
                    page.text(it.artist, 38f, theme.secondary, maxLines = 2)
                    page.gap(36f)
                    page.text(labels.text("{0} за период", labels.quantity(it.playCountInPeriod, "запуск", "запуска", "запусков")), 42f, theme.accent)
                } ?: page.text(labels.text("Пока нет трека на повторе"), 42f, theme.secondary, maxLines = 2)
            }
            WrappedShareSection.PULSE -> {
                page.text(labels.text("Когда звучала музыка"), 64f, serif = true, maxLines = 2)
                page.gap(36f)
                if (stats.timeOfDayDistribution.isEmpty()) page.text(labels.text("Пока нет данных"), 40f, theme.secondary)
                for ((slot, fraction) in stats.timeOfDayDistribution) {
                    val label = when (slot) {
                        com.lirix.app.analytics.TimeOfDaySlot.NIGHT -> labels.text("Ночью")
                        com.lirix.app.analytics.TimeOfDaySlot.MORNING -> labels.text("Утром")
                        com.lirix.app.analytics.TimeOfDaySlot.AFTERNOON -> labels.text("Днём")
                        com.lirix.app.analytics.TimeOfDaySlot.EVENING -> labels.text("Вечером")
                    }
                    page.text(labels.text("{0} · {1}% запусков", label, (fraction * 100).toInt()), 44f)
                    page.gap(26f)
                }
            }
            else -> {
                if (section != WrappedShareSection.TRACKS) {
                    page.text(time, if (page.stories) 112f else 94f, serif = true)
                    page.text(labels.text("в музыке за выбранный период"), 32f, theme.secondary)
                    page.gap(32f)
                    page.text(labels.quantity(stats.uniqueTracksCount, labels.text("трек"), labels.text("трека"), labels.text("треков")) + " · " +
                        labels.quantity(stats.uniqueArtistsCount, labels.text("артист"), labels.text("артиста"), labels.text("артистов")), 38f)
                    page.gap(36f)
                }
                page.text(labels.text("Любимые треки"), 48f, serif = true)
                page.gap(22f)
                if (stats.topTracks.isEmpty()) page.text(labels.text("Здесь появится ваша музыка"), 36f, theme.secondary)
                stats.topTracks.take(if (page.stories) 5 else 3).forEachIndexed { index, track ->
                    page.trackRow(index + 1, track.title, track.artist, track.playCount, artwork[track.trackKey])
                }
            }
        }
        return page.finish()
    }

    fun nowPlaying(
        track: TrackEntity, lyric: String?, format: ShareCardFormat,
        theme: PosterThemeSnapshot, art: Bitmap?, labels: PosterTextSnapshot
    ): Bitmap {
        val page = Page(format, theme, labels)
        page.text(labels.text("Lirix · Сейчас играет"), 30f, theme.secondary)
        page.gap(if (page.stories) 48f else 24f)
        val side = if (page.stories) 680f else 360f
        page.artwork(art, side)
        page.gap(if (page.stories) 48f else 28f)
        page.text(track.title.ifBlank { labels.text("Без названия") }, if (page.stories) 70f else 54f, serif = true, maxLines = 2)
        page.gap(12f)
        page.text(track.artist, 36f, theme.secondary, maxLines = 2)
        if (!lyric.isNullOrBlank()) {
            page.gap(36f)
            page.text(lyric, if (page.stories) 48f else 36f, theme.accent, serif = true, maxLines = 4)
        }
        return page.finish()
    }

    private fun periodLabel(timeframe: AnalyticsTimeframe, labels: PosterTextSnapshot) = when (timeframe) {
        AnalyticsTimeframe.TODAY -> labels.text("Сегодня")
        AnalyticsTimeframe.WEEK -> labels.text("Последние 7 дней")
        AnalyticsTimeframe.MONTH -> labels.text("Последние 30 дней")
        AnalyticsTimeframe.ALL_TIME -> labels.text("За всё время")
    }

    private fun quantity(value: Int, one: String, few: String, many: String): String {
        val ending = when {
            value % 100 in 11..14 -> many
            value % 10 == 1 -> one
            value % 10 in 2..4 -> few
            else -> many
        }
        return "$value $ending"
    }

    private class Page(format: ShareCardFormat, val theme: PosterThemeSnapshot, val labels: PosterTextSnapshot) {
        val stories = format == ShareCardFormat.STORIES_9_16
        private val bitmap = Bitmap.createBitmap(format.width, format.height, Bitmap.Config.ARGB_8888)
        private val canvas = Canvas(bitmap)
        private val margin = 72f
        private val contentWidth = bitmap.width - margin * 2
        private val bottom = bitmap.height - if (stories) 240f else 112f
        private var y = if (stories) 180f else margin

        init { canvas.drawColor(theme.background) }
        fun gap(size: Float) { y += if (stories) size else size * 0.5f }

        fun text(value: String, size: Float, color: Int = theme.primary, serif: Boolean = false, maxLines: Int = 1) {
            if (value.isBlank() || y >= bottom) return
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                textSize = size
                typeface = Typeface.create(if (serif) Typeface.SERIF else Typeface.SANS_SERIF, Typeface.NORMAL)
            }
            val remainingLines = ((bottom - y) / (size * 1.25f)).toInt().coerceAtMost(maxLines)
            if (remainingLines < 1) return
            val layout = StaticLayout.Builder.obtain(value, 0, value.length, paint, contentWidth.toInt())
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false)
                .setLineSpacing(0f, 1.25f).setMaxLines(remainingLines)
                .setEllipsize(TextUtils.TruncateAt.END).build()
            canvas.save()
            canvas.translate(margin, y)
            layout.draw(canvas)
            canvas.restore()
            y += layout.height
        }

        fun artwork(art: Bitmap?, side: Float) {
            val rect = RectF((bitmap.width - side) / 2, y, (bitmap.width + side) / 2, y + side)
            drawArt(art, rect)
            y += side
        }

        fun trackRow(rank: Int, title: String, artist: String, count: Int, art: Bitmap?) {
            val rowHeight = if (stories) 156f else 120f
            if (y + rowHeight > bottom) return
            val artSize = if (stories) 112f else 88f
            drawArt(art, RectF(margin, y, margin + artSize, y + artSize))
            val x = margin + artSize + 24f
            val available = (bitmap.width - margin - x).toInt()
            fun label(value: String, top: Float, size: Float, color: Int, lines: Int) {
                val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = color; textSize = size; typeface = Typeface.DEFAULT
                }
                val layout = StaticLayout.Builder.obtain(value, 0, value.length, paint, available)
                    .setIncludePad(false).setMaxLines(lines).setEllipsize(TextUtils.TruncateAt.END).build()
                canvas.save(); canvas.translate(x, top); layout.draw(canvas); canvas.restore()
            }
            label("$rank. $title", y, if (stories) 40f else 34f, theme.primary, 1)
            label(artist, y + if (stories) 48f else 42f, 28f, theme.secondary, 1)
            label(labels.quantity(count, labels.text("запуск"), labels.text("запуска"), labels.text("запусков")), y + if (stories) 86f else 76f, 25f, theme.accent, 1)
            y += rowHeight
        }

        private fun drawArt(art: Bitmap?, target: RectF) {
            canvas.drawRect(target, Paint().apply { color = theme.surface })
            if (art == null || art.isRecycled || art.width == 0 || art.height == 0) return
            val crop = minOf(art.width, art.height)
            val source = Rect((art.width - crop) / 2, (art.height - crop) / 2,
                (art.width + crop) / 2, (art.height + crop) / 2)
            canvas.drawBitmap(art, source, target, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        }

        fun finish(): Bitmap {
            val footerY = bitmap.height - if (stories) 180f else 72f
            canvas.drawLine(margin, footerY - 32f, bitmap.width - margin, footerY - 32f,
                Paint().apply { color = theme.border; strokeWidth = 2f })
            canvas.drawText(labels.text("Lirix · Музыка, которую вы выбрали"), margin, footerY,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = theme.secondary; textSize = 26f })
            return bitmap
        }
    }
}
