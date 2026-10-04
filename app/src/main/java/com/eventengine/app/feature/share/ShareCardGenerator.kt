package com.eventengine.app.feature.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.eventengine.app.analytics.AnalyticsTimeframe
import com.eventengine.app.analytics.TimeOfDaySlot
import com.eventengine.app.analytics.WrappedStats
import com.eventengine.app.storage.TrackEntity
import java.io.File

/** What to put on the Wrapped poster: one focused topic per card keeps it clean. */
enum class WrappedShareSection(val titleRu: String, val hintRu: String) {
    ARCHETYPE("Архетип", "Твой тип слушателя и часы в эфире"),
    ARTISTS("Топ артистов", "Любимые исполнители периода"),
    TRACKS("Топ треков", "Что крутилось чаще всего"),
    OBSESSION("Одержимость", "Один трек на репите"),
    PULSE("Пульс суток", "Когда ты слушаешь музыку"),
    FULL("Всё вместе", "Полный постер со всеми блоками")
}

enum class CollageMode { TILTED, BLUR, NONE }

enum class TitleFace { SANS, MONO, SERIF }

/**
 * Ten distinct poster looks. Each generation can pick a random one, so exports vary:
 * palette, background tint, cover-wall treatment (tilt/scale/blur/none) and title typeface.
 * c1..c4 play the roles of violet / cyan / gold / mint accents in the layout.
 */
enum class PosterStyle(
    val label: String,
    val bg: Int,
    val c1: Int,
    val c2: Int,
    val c3: Int,
    val c4: Int,
    val mode: CollageMode,
    val tilt: Float,
    val tileScale: Float,
    val collageAlpha: Int,
    val face: TitleFace,
    val glowMirror: Boolean
) {
    OBSIDIAN("Obsidian Pulse", 0xFF000000.toInt(), 0xFFB388FF.toInt(), 0xFF00E5FF.toInt(), 0xFFFFD700.toInt(), 0xFF00F5A0.toInt(), CollageMode.TILTED, -8.5f, 0.34f, 115, TitleFace.SANS, false),
    SOLAR("Solar Flare", 0xFF0A0300.toInt(), 0xFFFF4081.toInt(), 0xFFFF9100.toInt(), 0xFFFFEA00.toInt(), 0xFFFF6E40.toInt(), CollageMode.TILTED, 6f, 0.50f, 130, TitleFace.SANS, true),
    TERMINAL("Mint Terminal", 0xFF010A06.toInt(), 0xFF00F5A0.toInt(), 0xFF00E5FF.toInt(), 0xFFB2FF59.toInt(), 0xFF69F0AE.toInt(), CollageMode.NONE, 0f, 0.3f, 0, TitleFace.MONO, false),
    ROSE_NOIR("Rose Noir", 0xFF080004.toInt(), 0xFFFF4081.toInt(), 0xFFF8BBD0.toInt(), 0xFFFFFFFF.toInt(), 0xFFFF80AB.toInt(), CollageMode.TILTED, 0f, 0.24f, 85, TitleFace.SERIF, true),
    ICE("Ice Blue", 0xFF00060C.toInt(), 0xFF82B1FF.toInt(), 0xFF00E5FF.toInt(), 0xFFE1F5FE.toInt(), 0xFF80D8FF.toInt(), CollageMode.TILTED, 14f, 0.42f, 110, TitleFace.SANS, false),
    GOLD_VINYL("Gold Vinyl", 0xFF050400.toInt(), 0xFFFFD700.toInt(), 0xFFFFAB00.toInt(), 0xFFFFF59D.toInt(), 0xFFFFC400.toInt(), CollageMode.NONE, 0f, 0.3f, 0, TitleFace.SERIF, true),
    ACID("Acid Lime", 0xFF030500.toInt(), 0xFFC6FF00.toInt(), 0xFFB388FF.toInt(), 0xFFFFEA00.toInt(), 0xFF76FF03.toInt(), CollageMode.TILTED, -18f, 0.30f, 140, TitleFace.MONO, false),
    CRIMSON("Crimson Stage", 0xFF060000.toInt(), 0xFFFF1744.toInt(), 0xFFFF9100.toInt(), 0xFFFFD740.toInt(), 0xFFFF5252.toInt(), CollageMode.BLUR, 0f, 0.3f, 110, TitleFace.SANS, true),
    OCEAN("Deep Ocean", 0xFF00050A.toInt(), 0xFF1DE9B6.toInt(), 0xFF448AFF.toInt(), 0xFF84FFFF.toInt(), 0xFF18FFFF.toInt(), CollageMode.BLUR, 0f, 0.3f, 100, TitleFace.SERIF, false),
    MONO("Mono Editorial", 0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFFBDBDBD.toInt(), 0xFFFFFFFF.toInt(), 0xFF9E9E9E.toInt(), CollageMode.NONE, 0f, 0.3f, 0, TitleFace.SERIF, false);

    fun titleTypeface(): android.graphics.Typeface = when (face) {
        TitleFace.SANS -> android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        TitleFace.MONO -> android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
        TitleFace.SERIF -> android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.BOLD_ITALIC)
    }

    companion object {
        fun random(): PosterStyle = values().random()
    }
}

enum class ShareCardFormat(val width: Int, val height: Int, val label: String) {
    STORIES_9_16(1080, 1920, "Истории (9:16)"),
    SQUARE_1_1(1080, 1080, "Квадрат (1:1)")
}

/**
 * World-Class Anti-Slop Share Card Renderer for Music Tracker.
 * Tuned with:
 * - DESIGN_VARIANCE = 7 (Asymmetric poster hierarchy, bold scale contrast, analog vinyl & sound motifs)
 * - MOTION_INTENSITY = 5 (Multi-source atmospheric ambient glow, specular reflections, neon gradients)
 * - VISUAL_DENSITY = 4 (Generous breathing room, airy padding, clear heroic focal points)
 *
 * Spec: TASK-SHR-01 / Taste Skill Refactor
 */
object ShareCardGenerator {

    private const val COLOR_AMOLED_BLACK = 0xFF000000.toInt()
    private const val COLOR_SURFACE_1 = 0xFF0D0E14.toInt()
    private const val COLOR_SURFACE_2 = 0xFF141622.toInt()
    private const val COLOR_SURFACE_3 = 0xFF1C1E2E.toInt()
    private const val COLOR_BORDER_SUBTLE = 0xFF242738.toInt()
    private const val COLOR_BORDER_HIGHLIGHT = 0xFF3D425C.toInt()

    private const val COLOR_TEXT_PRIMARY = 0xFFF4F4F8.toInt()
    private const val COLOR_TEXT_SECONDARY = 0xFFA2A5B8.toInt()
    private const val COLOR_TEXT_MUTED = 0xFF66697D.toInt()

    private const val COLOR_HYPER_VIOLET = 0xFFB388FF.toInt()
    private const val COLOR_HYPER_VIOLET_DEEP = 0xFF7C4DFF.toInt()
    private const val COLOR_CYBER_CYAN = 0xFF00E5FF.toInt()
    private const val COLOR_ELECTRIC_MINT = 0xFF00F5A0.toInt()
    private const val COLOR_AMBER_GOLD = 0xFFFFD700.toInt()

    /**
     * Generates a world-class Wrapped poster card with dynamic album art collage background,
     * bold editorial hierarchy, and zero slop.
     * Tuned with:
     * - DESIGN_VARIANCE = 10 (Dynamic tilted album cover wall, heroic scale contrast, raw poster energy)
     * - MOTION_INTENSITY = 7 (Vivid atmospheric ambient glow, multi-point neon flares, cinematic lighting)
     * - VISUAL_DENSITY = 4 (Generous breathing room, clear heroic focal points, no nested boring boxes)
     */
    fun generateWrappedCard(
        context: Context,
        stats: WrappedStats,
        timeframe: AnalyticsTimeframe,
        format: ShareCardFormat,
        obsessionBitmap: Bitmap? = null,
        topCoverBitmaps: List<Bitmap> = emptyList(),
        section: WrappedShareSection = WrappedShareSection.FULL,
        posterStyle: PosterStyle = PosterStyle.OBSIDIAN
    ): Bitmap {
        // Local palette shadows the default constants so the whole layout re-colors per style
        val COLOR_HYPER_VIOLET = posterStyle.c1
        val COLOR_CYBER_CYAN = posterStyle.c2
        val COLOR_AMBER_GOLD = posterStyle.c3
        val COLOR_ELECTRIC_MINT = posterStyle.c4
        val showArchetype = section == WrappedShareSection.FULL || section == WrappedShareSection.ARCHETYPE
        val showArtists = section == WrappedShareSection.FULL || section == WrappedShareSection.ARTISTS
        val showObsession = section == WrappedShareSection.FULL || section == WrappedShareSection.OBSESSION
        val showTracks = section == WrappedShareSection.FULL || section == WrappedShareSection.TRACKS
        val showPulse = section == WrappedShareSection.FULL || section == WrappedShareSection.PULSE
        val width = format.width
        val height = format.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val isStories = format == ShareCardFormat.STORIES_9_16

        // 1. Pure AMOLED Black Base
        canvas.drawColor(posterStyle.bg)

        // 2. Dynamic Tilted Album Art Collage Wall Background
        drawAlbumCoverCollage(canvas, width, height, topCoverBitmaps, stats, posterStyle)

        // 3. Multi-point atmospheric nebula glow (MOTION_INTENSITY = 7)
        drawAtmosphericGlow(canvas, width, height, posterStyle)

        // 4. Background vinyl groove line-art
        drawBackgroundGrooveArcs(canvas, width, height)

        val padding = width * 0.08f
        var curY = height * (if (isStories) 0.055f else 0.065f)

        if (isStories || section != WrappedShareSection.FULL) {
            // ==================== FOCUSED POSTER LAYOUT (STORIES + SQUARE) ====================

            // Header Bar: Pill badge + Monospace timeframe tag
            val headerPillH = 44f
            val brandTag = "✦ MUSIC TRACKER // REPLAY '26"
            val brandPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_CYBER_CYAN
                textSize = width * 0.026f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                letterSpacing = 0.08f
            }
            val pillW = brandPaint.measureText(brandTag) + 36f
            val pillRect = RectF(padding, curY, padding + pillW, curY + headerPillH)
            val pillBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x900D0E14.toInt()
                style = Paint.Style.FILL
            }
            val pillBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x8800E5FF.toInt()
                style = Paint.Style.STROKE
                strokeWidth = 2f
            }
            canvas.drawRoundRect(pillRect, 22f, 22f, pillBg)
            canvas.drawRoundRect(pillRect, 22f, 22f, pillBorder)
            canvas.drawText(brandTag, padding + 18f, curY + (headerPillH * 0.65f), brandPaint)

            // Right side: timeframe badge
            val timeLabel = "[ ${timeframe.labelRu.uppercase()} ]"
            val timePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_TEXT_MUTED
                textSize = width * 0.025f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                textAlign = Paint.Align.RIGHT
            }
            canvas.drawText(timeLabel, width - padding, curY + (headerPillH * 0.65f), timePaint)

            curY += headerPillH + 68f

            if (showArchetype) {
            // Hero Archetype Showcase (Bold Editorial Poster, DESIGN_VARIANCE = 10)
            val archTagText = "★ ВАШ МУЗЫКАЛЬНЫЙ АРХЕТИП"
            val archTagPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_AMBER_GOLD
                textSize = width * 0.027f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                letterSpacing = 0.10f
            }
            canvas.drawText(archTagText, padding, curY, archTagPaint)
            curY += 92f

            // Hero Archetype Title (Massive Scale Contrast)
            val archTitleText = stats.archetype.titleRu.uppercase()
            val archTitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_TEXT_PRIMARY
                textSize = width * 0.076f
                typeface = posterStyle.titleTypeface()
                letterSpacing = -0.02f
                setShadowLayer(28f, 0f, 0f, (COLOR_HYPER_VIOLET and 0x00FFFFFF) or 0x99000000.toInt())
            }
            canvas.drawText(archTitleText, padding, curY, archTitlePaint)
            curY += 46f

            // Archetype Narrative
            val archDescPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_TEXT_SECONDARY
                textSize = width * 0.033f
                letterSpacing = 0.01f
            }
            drawMultilineText(
                canvas,
                stats.archetype.descriptionRu,
                archDescPaint,
                padding,
                curY,
                (width - (padding * 2f)).toInt(),
                3
            )

            curY += 130f

            // Hero Big-Number Stat Block (Impeccable: Scale Contrast)
            val totalMinutes = stats.totalListeningTimeMs / 60000
            val totalHours = totalMinutes / 60
            val heroNumStr = if (totalHours > 0) "$totalHours" else "$totalMinutes"
            val heroUnitWord = if (totalHours > 0) {
                pluralizeWordOnly(totalHours.toInt(), "ЧАС", "ЧАСА", "ЧАСОВ")
            } else {
                pluralizeWordOnly(totalMinutes.toInt(), "МИНУТА", "МИНУТЫ", "МИНУТ")
            }
            val heroUnitStr = "$heroUnitWord В ЭФИРЕ"

            val bigNumPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = width * 0.16f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                letterSpacing = -0.04f
                shader = LinearGradient(
                    padding, curY,
                    padding, curY + (width * 0.16f),
                    COLOR_HYPER_VIOLET, COLOR_CYBER_CYAN,
                    Shader.TileMode.CLAMP
                )
                setShadowLayer(32f, 0f, 0f, 0x6600E5FF.toInt())
            }
            val bigNumW = bigNumPaint.measureText(heroNumStr)
            val numBaseY = curY + (width * 0.14f)
            canvas.drawText(heroNumStr, padding, numBaseY, bigNumPaint)

            // Text alongside the big number
            val labelX = padding + bigNumW + 36f
            val unitPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_TEXT_PRIMARY
                textSize = width * 0.042f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                letterSpacing = 0.03f
            }
            canvas.drawText(heroUnitStr, labelX, curY + 54f, unitPaint)

            val subLabelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_TEXT_MUTED
                textSize = width * 0.027f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }
            canvas.drawText("МУЗЫКАЛЬНОГО ПОТОКА", labelX, curY + 98f, subLabelPaint)

            val counterPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_ELECTRIC_MINT
                textSize = width * 0.027f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            }
            val tracksCountStr = pluralize(stats.uniqueTracksCount, "трек", "трека", "треков")
            val artistsCountStr = pluralize(stats.uniqueArtistsCount, "артист", "артиста", "артистов")
            canvas.drawText("⚡ $tracksCountStr • $artistsCountStr", labelX, curY + 142f, counterPaint)

            curY += (width * 0.18f) + 50f
            } else {
                curY += when (section) {
                    WrappedShareSection.OBSESSION -> if (isStories) 520f else 220f
                    WrappedShareSection.PULSE -> if (isStories) 380f else 120f
                    else -> 90f
                }
            }

            // Top Artists Podium / Editorial Chart (No generic nested boxes)
            val top3Artists = (if (showArtists) stats.topArtists else emptyList())
                .filter { !it.artist.equals("Unknown Artist", true) && !it.artist.equals("Неизвестный исполнитель", true) }
                .take(if (section == WrappedShareSection.ARTISTS) 5 else 3)
            if (top3Artists.isNotEmpty()) {
                val maxArtistPlays = top3Artists.maxOfOrNull { it.playCount }?.coerceAtLeast(1) ?: 1

                val sectionTitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = COLOR_TEXT_PRIMARY
                    textSize = width * 0.034f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    letterSpacing = 0.04f
                }
                canvas.drawText("ТОП ИСПОЛНИТЕЛИ // DNA", padding, curY, sectionTitlePaint)
                curY += 42f

                val rowH = 88f
                val rankColors = listOf(COLOR_AMBER_GOLD, COLOR_CYBER_CYAN, COLOR_HYPER_VIOLET)

                for ((idx, artistItem) in top3Artists.withIndex()) {
                    val rowRect = RectF(padding, curY, width - padding, curY + rowH)
                    val rankColor = rankColors.getOrElse(idx) { COLOR_TEXT_SECONDARY }

                    // Row background with subtle glass tint
                    val rowBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0x750D0E14.toInt()
                        style = Paint.Style.FILL
                    }
                    val rowBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0x453D425C.toInt()
                        style = Paint.Style.STROKE
                        strokeWidth = 1.5f
                    }
                    canvas.drawRoundRect(rowRect, 18f, 18f, rowBg)
                    canvas.drawRoundRect(rowRect, 18f, 18f, rowBorder)

                    // Rank badge e.g. #01
                    val rankText = "#0${idx + 1}"
                    val rankPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = rankColor
                        textSize = width * 0.034f
                        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                    }
                    canvas.drawText(rankText, rowRect.left + 24f, rowRect.top + 38f, rankPaint)

                    // Artist name & play count with correct Russian pluralization
                    val namePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = COLOR_TEXT_PRIMARY
                        textSize = width * 0.036f
                        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    }
                    val nameX = rowRect.left + 116f
                    val countText = pluralize(artistItem.playCount, "трек", "трека", "треков")
                    val countPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = COLOR_TEXT_MUTED
                        textSize = width * 0.026f
                        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
                        textAlign = Paint.Align.RIGHT
                    }
                    val countW = countPaint.measureText(countText)
                    val safeNameW = (rowRect.right - nameX - countW - 40f).coerceAtLeast(100f)
                    val truncatedName = ellipsize(artistItem.artist, namePaint, safeNameW)

                    canvas.drawText(truncatedName, nameX, rowRect.top + 38f, namePaint)
                    canvas.drawText(countText, rowRect.right - 24f, rowRect.top + 38f, countPaint)

                    // Glowing Progress bar beneath artist with clean dedicated breathing room
                    val barLeft = nameX
                    val barRight = rowRect.right - 24f
                    val barTotalW = barRight - barLeft
                    val barFraction = (artistItem.playCount.toFloat() / maxArtistPlays.toFloat()).coerceIn(0.05f, 1f)
                    val barTop = rowRect.top + 58f

                    val barBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0x40141622.toInt()
                        style = Paint.Style.FILL
                    }
                    val barFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = rankColor
                        style = Paint.Style.FILL
                    }
                    canvas.drawRoundRect(RectF(barLeft, barTop, barRight, barTop + 6f), 3f, 3f, barBgPaint)
                    canvas.drawRoundRect(RectF(barLeft, barTop, barLeft + (barTotalW * barFraction), barTop + 6f), 3f, 3f, barFillPaint)

                    curY += rowH + 14f
                }
                curY += 24f
            }

            // Top Obsession Luxury Card
            val obsession = stats.topObsession
            if (obsession != null && showObsession) {
                val obsH = 200f
                val obsRect = RectF(padding, curY, width - padding, curY + obsH)

                val obsBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(
                        obsRect.left, obsRect.top,
                        obsRect.right, obsRect.bottom,
                        0xD9241604.toInt(), 0xD90D0902.toInt(),
                        Shader.TileMode.CLAMP
                    )
                }
                val obsBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xCCFFD700.toInt()
                    style = Paint.Style.STROKE
                    strokeWidth = 2f
                }
                canvas.drawRoundRect(obsRect, 24f, 24f, obsBg)
                canvas.drawRoundRect(obsRect, 24f, 24f, obsBorder)

                // Header pill inside obsession
                val obsHeaderPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = COLOR_AMBER_GOLD
                    textSize = width * 0.026f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    letterSpacing = 0.06f
                }
                canvas.drawText("🔥 ГЛАВНАЯ ОДЕРЖИМОСТЬ ПЕРИОДА", obsRect.left + 26f, obsRect.top + 38f, obsHeaderPaint)

                // Repeat badge with correct Russian grammar (e.g. x4 повтора)
                val repeatWord = pluralizeWordOnly(obsession.playCountInPeriod, "повтор", "повтора", "повторов")
                val repeatStr = "x${obsession.playCountInPeriod} $repeatWord"
                val repeatPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = COLOR_AMBER_GOLD
                    textSize = width * 0.026f
                    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                    textAlign = Paint.Align.RIGHT
                }
                canvas.drawText(repeatStr, obsRect.right - 26f, obsRect.top + 38f, repeatPaint)

                // Vinyl / Album thumbnail
                val thumbSize = 100f
                val thumbLeft = obsRect.left + 26f
                val thumbTop = obsRect.top + 60f
                val thumbRect = RectF(thumbLeft, thumbTop, thumbLeft + thumbSize, thumbTop + thumbSize)

                if (obsessionBitmap != null) {
                    drawRoundedBitmap(canvas, obsessionBitmap, thumbRect, 18f)
                } else {
                    drawPlaceholderArt(canvas, thumbRect, 18f)
                }

                val textX = thumbLeft + thumbSize + 22f
                val maxObsTextW = obsRect.right - textX - 24f

                val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = COLOR_TEXT_PRIMARY
                    textSize = width * 0.038f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                val artistPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = COLOR_TEXT_SECONDARY
                    textSize = width * 0.029f
                }
                val safeTitle = ellipsize(obsession.title, titlePaint, maxObsTextW)
                val safeArtist = ellipsize(obsession.artist, artistPaint, maxObsTextW)

                canvas.drawText(safeTitle, textX, thumbTop + 40f, titlePaint)
                canvas.drawText(safeArtist, textX, thumbTop + 80f, artistPaint)

                curY += obsH + 30f
            }

            // Top Tracks Hit Rotation Section
            val topTracksToShow = if (!showTracks) emptyList() else stats.topTracks.take(
                if (section == WrappedShareSection.TRACKS) 5 else if (obsession != null) 2 else 3
            )
            if (topTracksToShow.isNotEmpty()) {
                val trackSectionTitle = "ТОП ТРЕКИ // HIT ROTATION"
                val trackSectionPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = COLOR_CYBER_CYAN
                    textSize = width * 0.032f
                    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                    letterSpacing = 0.06f
                }
                canvas.drawText(trackSectionTitle, padding, curY + 24f, trackSectionPaint)
                curY += 48f

                val trackRowH = 102f
                val trackGap = 12f
                val rankColors = listOf(COLOR_AMBER_GOLD, COLOR_CYBER_CYAN, COLOR_HYPER_VIOLET, COLOR_ELECTRIC_MINT)

                for ((idx, trackItem) in topTracksToShow.withIndex()) {
                    val rowRect = RectF(padding, curY, width - padding, curY + trackRowH)
                    val rankColor = rankColors.getOrElse(idx) { COLOR_TEXT_SECONDARY }

                    // Row background with subtle glass tint & sleek border
                    val rowBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0x750D0E14.toInt()
                        style = Paint.Style.FILL
                    }
                    val rowBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0x403D425C.toInt()
                        style = Paint.Style.STROKE
                        strokeWidth = 1.5f
                    }
                    canvas.drawRoundRect(rowRect, 18f, 18f, rowBg)
                    canvas.drawRoundRect(rowRect, 18f, 18f, rowBorder)

                    // Rank badge e.g. #01
                    val rankText = "#0${idx + 1}"
                    val rankPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = rankColor
                        textSize = width * 0.032f
                        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                    }
                    canvas.drawText(rankText, rowRect.left + 22f, rowRect.top + (trackRowH * 0.58f), rankPaint)

                    // Album Art Thumbnail / Fallback
                    val thumbSize = trackRowH - 26f
                    val thumbLeft = rowRect.left + 108f
                    val thumbTop = rowRect.top + 13f
                    val thumbRect = RectF(thumbLeft, thumbTop, thumbLeft + thumbSize, thumbTop + thumbSize)

                    var trackArtBitmap: Bitmap? = topCoverBitmaps.getOrNull(idx)
                    if (trackArtBitmap == null && trackItem.albumArtUri != null) {
                        try {
                            val f = File(trackItem.albumArtUri.removePrefix("file://"))
                            if (f.exists() && f.length() > 0L) {
                                trackArtBitmap = BitmapFactory.decodeFile(f.absolutePath)
                            }
                        } catch (_: Exception) {}
                    }

                    if (trackArtBitmap != null) {
                        drawRoundedBitmap(canvas, trackArtBitmap, thumbRect, 14f)
                    } else {
                        drawPlaceholderArt(canvas, thumbRect, 14f)
                    }

                    // Stat pill / badge on right: e.g. "12 раз" or duration
                    val statBadgeText = if (trackItem.playCount > 0) pluralize(trackItem.playCount, "раз", "раза", "раз") else "${trackItem.durationMs / 60000} мин"
                    val statBadgePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = rankColor
                        textSize = width * 0.025f
                        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                        textAlign = Paint.Align.CENTER
                    }
                    val badgeTextW = statBadgePaint.measureText(statBadgeText)
                    val pillW = badgeTextW + 30f
                    val pillH = 38f
                    val pillRect = RectF(
                        rowRect.right - pillW - 20f,
                        rowRect.top + (trackRowH - pillH) / 2f,
                        rowRect.right - 20f,
                        rowRect.top + (trackRowH + pillH) / 2f
                    )
                    val pillBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0x50141622.toInt()
                        style = Paint.Style.FILL
                    }
                    val pillBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0x403D425C.toInt()
                        style = Paint.Style.STROKE
                        strokeWidth = 1.2f
                    }
                    canvas.drawRoundRect(pillRect, 12f, 12f, pillBg)
                    canvas.drawRoundRect(pillRect, 12f, 12f, pillBorder)
                    canvas.drawText(statBadgeText, pillRect.centerX(), pillRect.centerY() + (statBadgePaint.textSize * 0.35f), statBadgePaint)

                    // Track title & artist
                    val textLeft = thumbLeft + thumbSize + 20f
                    val maxTitleW = (pillRect.left - textLeft - 16f).coerceAtLeast(100f)

                    val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = COLOR_TEXT_PRIMARY
                        textSize = width * 0.034f
                        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    }
                    val artistPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = COLOR_TEXT_SECONDARY
                        textSize = width * 0.026f
                    }

                    val truncatedTitle = ellipsize(trackItem.title, titlePaint, maxTitleW)
                    val truncatedArtist = ellipsize(trackItem.artist, artistPaint, maxTitleW)

                    canvas.drawText(truncatedTitle, textLeft, rowRect.top + (trackRowH * 0.44f), titlePaint)
                    canvas.drawText(truncatedArtist, textLeft, rowRect.top + (trackRowH * 0.78f), artistPaint)

                    curY += trackRowH + trackGap
                }
                curY += 20f
            }

            // ==================== LISTENING DNA & PULSE COMPASS ====================
            // Fills the vertical rhythm and balances the lower canvas before footer
            if (showPulse) {
            val peakEntry = stats.timeOfDayDistribution.maxByOrNull { it.value }
            val peakSlot = peakEntry?.key ?: TimeOfDaySlot.NIGHT
            val peakFraction = peakEntry?.value ?: 0.35f
            val (slotIcon, slotTitle) = when (peakSlot) {
                TimeOfDaySlot.NIGHT -> "🌙" to "НОЧНОЙ ПИК (00-06)"
                TimeOfDaySlot.MORNING -> "🌅" to "УТРО (06-12)"
                TimeOfDaySlot.AFTERNOON -> "☀️" to "ДЕНЬ (12-18)"
                TimeOfDaySlot.EVENING -> "🌆" to "ВЕЧЕР (18-24)"
            }
            val peakPercentStr = "${(peakFraction * 100).toInt()}%"
            val lyricsPercentStr = "${(stats.lyricsCoverageRatio * 100).toInt()}%"

            if (topTracksToShow.isEmpty()) {
                // Expanded Infographic Card when tracks list is empty or minimal
                val cardH = 260f
                val cardRect = RectF(padding, curY, width - padding, curY + cardH)
                val cardBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0x800D0E14.toInt()
                    style = Paint.Style.FILL
                }
                val cardBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0x453D425C.toInt()
                    style = Paint.Style.STROKE
                    strokeWidth = 1.5f
                }
                canvas.drawRoundRect(cardRect, 22f, 22f, cardBg)
                canvas.drawRoundRect(cardRect, 22f, 22f, cardBorder)

                val headPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = COLOR_CYBER_CYAN
                    textSize = width * 0.028f
                    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                    letterSpacing = 0.06f
                }
                canvas.drawText("🧭 КОМПАС АКТИВНОСТИ // ВРЕМЯ СУТОК", cardRect.left + 24f, cardRect.top + 40f, headPaint)

                // 4 Time of day distribution progress bars
                val slotList = listOf(
                    Triple(TimeOfDaySlot.NIGHT, "Ночь (00-06)", COLOR_HYPER_VIOLET),
                    Triple(TimeOfDaySlot.MORNING, "Утро (06-12)", COLOR_AMBER_GOLD),
                    Triple(TimeOfDaySlot.AFTERNOON, "День (12-18)", COLOR_CYBER_CYAN),
                    Triple(TimeOfDaySlot.EVENING, "Вечер (18-24)", COLOR_ELECTRIC_MINT)
                )

                var slotY = cardRect.top + 78f
                for ((slot, label, slotColor) in slotList) {
                    val frac = stats.timeOfDayDistribution[slot] ?: 0f
                    val pct = (frac * 100).toInt()

                    val lblPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = COLOR_TEXT_SECONDARY
                        textSize = width * 0.024f
                    }
                    canvas.drawText(label, cardRect.left + 24f, slotY + 12f, lblPaint)

                    val barL = cardRect.left + 210f
                    val barR = cardRect.right - 90f
                    val barW = barR - barL

                    val barBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0x301E2235.toInt()
                        style = Paint.Style.FILL
                    }
                    val barFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = slotColor
                        style = Paint.Style.FILL
                    }
                    canvas.drawRoundRect(RectF(barL, slotY + 2f, barR, slotY + 12f), 5f, 5f, barBg)
                    canvas.drawRoundRect(RectF(barL, slotY + 2f, barL + (barW * frac.coerceIn(0f, 1f)), slotY + 12f), 5f, 5f, barFill)

                    val pctPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = COLOR_TEXT_PRIMARY
                        textSize = width * 0.024f
                        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                        textAlign = Paint.Align.RIGHT
                    }
                    canvas.drawText("$pct%", cardRect.right - 24f, slotY + 12f, pctPaint)

                    slotY += 38f
                }

                // Bottom feature tag inside the card
                val featPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = COLOR_ELECTRIC_MINT
                    textSize = width * 0.024f
                    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                }
                val repeatRatioStr = if (stats.repeatRatio > 0f) String.format(java.util.Locale.US, "%.1f", stats.repeatRatio) else "1.0"
                canvas.drawText("⚡ $lyricsPercentStr С ТЕКСТАМИ // x$repeatRatioStr ИНДЕКС ПОВТОРОВ", cardRect.left + 24f, cardRect.bottom - 20f, featPaint)

                curY += cardH + 28f
            } else {
                // Dual modular telemetry tiles when top tracks are present
                val tileH = 176f
                val colW = (width - (padding * 2f) - 20f) / 2f

                // Left Tile: Peak Time Compass
                val leftRect = RectF(padding, curY, padding + colW, curY + tileH)
                val tileBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0x800D0E14.toInt()
                    style = Paint.Style.FILL
                }
                val tileBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0x453D425C.toInt()
                    style = Paint.Style.STROKE
                    strokeWidth = 1.5f
                }
                canvas.drawRoundRect(leftRect, 20f, 20f, tileBg)
                canvas.drawRoundRect(leftRect, 20f, 20f, tileBorder)

                val tagPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = COLOR_CYBER_CYAN
                    textSize = width * 0.023f
                    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                    letterSpacing = 0.05f
                }
                canvas.drawText("🧭 ПИК ВРЕМЕНИ", leftRect.left + 20f, leftRect.top + 34f, tagPaint)

                val bigStatPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = COLOR_TEXT_PRIMARY
                    textSize = width * 0.060f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                canvas.drawText(peakPercentStr, leftRect.left + 20f, leftRect.top + 108f, bigStatPaint)

                val subSlotPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = COLOR_ELECTRIC_MINT
                    textSize = width * 0.023f
                    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                }
                canvas.drawText("$slotIcon $slotTitle", leftRect.left + 20f, leftRect.top + 152f, subSlotPaint)

                // Right Tile: Lyrics & Karaoke Ratio
                val rightRect = RectF(padding + colW + 20f, curY, width - padding, curY + tileH)
                canvas.drawRoundRect(rightRect, 20f, 20f, tileBg)
                canvas.drawRoundRect(rightRect, 20f, 20f, tileBorder)

                val tagLyricsPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = COLOR_HYPER_VIOLET
                    textSize = width * 0.023f
                    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                    letterSpacing = 0.05f
                }
                canvas.drawText("⚡ С ТЕКСТАМИ", rightRect.left + 20f, rightRect.top + 34f, tagLyricsPaint)

                val bigLyricsPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = COLOR_TEXT_PRIMARY
                    textSize = width * 0.060f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                canvas.drawText(lyricsPercentStr, rightRect.left + 20f, rightRect.top + 108f, bigLyricsPaint)

                val subLyricsPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = COLOR_TEXT_MUTED
                    textSize = width * 0.023f
                    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
                }
                val repeatRatioStr = if (stats.repeatRatio > 0f) String.format(java.util.Locale.US, "%.1f", stats.repeatRatio) else "1.0"
                canvas.drawText("x$repeatRatioStr ИНДЕКС ПОВТОРА", rightRect.left + 20f, rightRect.top + 152f, subLyricsPaint)

                curY += tileH + 28f
            }
            } // end showPulse

        } else {
            // ==================== SQUARE 1:1 MASTER POSTER ====================

            // Header
            val headerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_CYBER_CYAN
                textSize = width * 0.030f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                letterSpacing = 0.08f
            }
            canvas.drawText("✦ MUSIC TRACKER // REPLAY '26", padding, curY + 28f, headerPaint)

            val timePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_TEXT_MUTED
                textSize = width * 0.026f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                textAlign = Paint.Align.RIGHT
            }
            canvas.drawText("[ ${timeframe.labelRu.uppercase()} ]", width - padding, curY + 28f, timePaint)

            curY += 80f

            // Archetype Showcase
            val archTagPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_AMBER_GOLD
                textSize = width * 0.026f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                letterSpacing = 0.08f
            }
            canvas.drawText("★ АРХЕТИП СЛУШАТЕЛЯ", padding, curY, archTagPaint)
            curY += 48f

            val archTitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_TEXT_PRIMARY
                textSize = width * 0.060f
                typeface = posterStyle.titleTypeface()
                letterSpacing = -0.02f
                setShadowLayer(20f, 0f, 0f, (COLOR_HYPER_VIOLET and 0x00FFFFFF) or 0x88000000.toInt())
            }
            canvas.drawText(stats.archetype.titleRu.uppercase(), padding, curY, archTitlePaint)
            curY += 70f

            // Split Grid: Left = Big Number, Right = Top Artists
            val colW = (width - (padding * 2f) - 32f) / 2f
            val splitTop = curY
            val splitH = 340f

            // Left Col: Stat Card
            val leftRect = RectF(padding, splitTop, padding + colW, splitTop + splitH)
            val leftBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x800D0E14.toInt()
                style = Paint.Style.FILL
            }
            val leftBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x55242738.toInt()
                style = Paint.Style.STROKE
                strokeWidth = 2f
            }
            canvas.drawRoundRect(leftRect, 24f, 24f, leftBg)
            canvas.drawRoundRect(leftRect, 24f, 24f, leftBorder)

            val totalMinutes = stats.totalListeningTimeMs / 60000
            val totalHours = totalMinutes / 60
            val heroNumStr = if (totalHours > 0) "$totalHours" else "$totalMinutes"
            val heroUnitStr = if (totalHours > 0) {
                pluralizeWordOnly(totalHours.toInt(), "ЧАС", "ЧАСА", "ЧАСОВ")
            } else {
                pluralizeWordOnly(totalMinutes.toInt(), "МИНУТА", "МИНУТЫ", "МИНУТ")
            }

            val numPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = width * 0.12f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                letterSpacing = -0.04f
                shader = LinearGradient(
                    leftRect.left + 24f, leftRect.top + 30f,
                    leftRect.left + 24f, leftRect.top + 130f,
                    COLOR_HYPER_VIOLET, COLOR_CYBER_CYAN,
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawText(heroNumStr, leftRect.left + 24f, leftRect.top + 120f, numPaint)

            val unitPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_TEXT_PRIMARY
                textSize = width * 0.038f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            canvas.drawText(heroUnitStr, leftRect.left + 24f, leftRect.top + 175f, unitPaint)

            val subPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_TEXT_MUTED
                textSize = width * 0.024f
            }
            canvas.drawText("МУЗЫКАЛЬНОГО ПОТОКА", leftRect.left + 24f, leftRect.top + 215f, subPaint)

            val statPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_ELECTRIC_MINT
                textSize = width * 0.025f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            }
            val sqTracksStr = pluralize(stats.uniqueTracksCount, "трек", "трека", "треков")
            val sqArtistsStr = pluralize(stats.uniqueArtistsCount, "арт.", "арт.", "арт.")
            canvas.drawText("⚡ $sqTracksStr • $sqArtistsStr", leftRect.left + 24f, leftRect.top + 280f, statPaint)

            // Right Col: Top Artists List
            val rightLeft = padding + colW + 32f
            val rightRect = RectF(rightLeft, splitTop, rightLeft + colW, splitTop + splitH)
            val rightBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x800D0E14.toInt()
                style = Paint.Style.FILL
            }
            val rightBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x55242738.toInt()
                style = Paint.Style.STROKE
                strokeWidth = 2f
            }
            canvas.drawRoundRect(rightRect, 24f, 24f, rightBg)
            canvas.drawRoundRect(rightRect, 24f, 24f, rightBorder)

            val rightTitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_TEXT_PRIMARY
                textSize = width * 0.028f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            canvas.drawText("ТОП АРТИСТЫ", rightLeft + 20f, splitTop + 44f, rightTitlePaint)

            val top2Artists = stats.topArtists.take(2)
            var artY = splitTop + 84f
            val rankColors = listOf(COLOR_AMBER_GOLD, COLOR_CYBER_CYAN)

            for ((idx, artistItem) in top2Artists.withIndex()) {
                val rankText = "#0${idx + 1}"
                val rColor = rankColors.getOrElse(idx) { COLOR_TEXT_SECONDARY }

                val rPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = rColor
                    textSize = width * 0.028f
                    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                }
                canvas.drawText(rankText, rightLeft + 20f, artY + 28f, rPaint)

                val aPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = COLOR_TEXT_PRIMARY
                    textSize = width * 0.030f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                val safeArtist = ellipsize(artistItem.artist, aPaint, colW - 120f)
                canvas.drawText(safeArtist, rightLeft + 76f, artY + 28f, aPaint)

                val cPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = COLOR_TEXT_MUTED
                    textSize = width * 0.023f
                    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
                }
                val sqArtistPlayText = pluralize(artistItem.playCount, "трек", "трека", "треков")
                canvas.drawText(sqArtistPlayText, rightLeft + 76f, artY + 62f, cPaint)

                artY += 100f
            }

            curY = splitTop + splitH + 40f

            // Bottom Obsession Strip (if available)
            val obsession = stats.topObsession
            if (obsession != null) {
                val obsStripH = 110f
                val obsRect = RectF(padding, curY, width - padding, curY + obsStripH)
                val obsBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(
                        obsRect.left, obsRect.top,
                        obsRect.right, obsRect.bottom,
                        0xD9241604.toInt(), 0xD90D0902.toInt(),
                        Shader.TileMode.CLAMP
                    )
                }
                val obsBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xAAFFD700.toInt()
                    style = Paint.Style.STROKE
                    strokeWidth = 1.5f
                }
                canvas.drawRoundRect(obsRect, 20f, 20f, obsBg)
                canvas.drawRoundRect(obsRect, 20f, 20f, obsBorder)

                val obsTitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = COLOR_AMBER_GOLD
                    textSize = width * 0.026f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                canvas.drawText("🔥 ОДЕРЖИМОСТЬ: ${obsession.title} — ${obsession.artist}", obsRect.left + 24f, obsRect.top + 48f, obsTitlePaint)

                val obsSubPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = COLOR_TEXT_MUTED
                    textSize = width * 0.024f
                    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
                }
                val sqRepeatWord = pluralizeWordOnly(obsession.playCountInPeriod, "раз", "раза", "раз")
                canvas.drawText("ПРОСЛУШАНО ${obsession.playCountInPeriod} $sqRepeatWord В ЭТОМ ПЕРИОДЕ", obsRect.left + 24f, obsRect.top + 84f, obsSubPaint)
            }
        }

        // Minimalist Hardware-Inspired Footer
        drawFooter(canvas, width, height)

        return bitmap
    }

    /**
     * Generates a Now Playing card with analog vinyl grooves, specular lighting, and glowing lyric quote.
     */
    fun generateNowPlayingCard(
        context: Context,
        track: TrackEntity,
        currentLyricLine: String?,
        format: ShareCardFormat,
        albumArtBitmap: Bitmap? = null
    ): Bitmap {
        val width = format.width
        val height = format.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val isStories = format == ShareCardFormat.STORIES_9_16

        // 1. Pure AMOLED Black Base
        canvas.drawColor(COLOR_AMOLED_BLACK)

        // 2. Center-radial vinyl lighting glow
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                width / 2f,
                height * 0.38f,
                width * 0.75f,
                intArrayOf(0x457C4DFF.toInt(), 0x18120826.toInt(), COLOR_AMOLED_BLACK),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), glowPaint)

        val padding = width * 0.08f
        var curY = height * (if (isStories) 0.06f else 0.07f)

        // 3. Header: "СЕЙЧАС ИГРАЕТ"
        val headerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_CYBER_CYAN
            textSize = width * 0.032f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            letterSpacing = 0.12f
        }
        canvas.drawText("● СЕЙЧАС ИГРАЕТ", padding, curY, headerPaint)

        val playerTag = track.sourcePackage.substringAfterLast('.').uppercase()
        val tagPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT_MUTED
            textSize = width * 0.024f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("[ $playerTag ]", width - padding, curY, tagPaint)

        curY += (if (isStories) 80f else 50f)

        // 4. Center Vinyl Disc with Specular Highlights
        val vinylRadius = if (isStories) width * 0.38f else width * 0.28f
        val vinylCenterX = width / 2f
        val vinylCenterY = curY + vinylRadius

        drawHighFidelityVinylDisc(canvas, vinylCenterX, vinylCenterY, vinylRadius, albumArtBitmap)

        curY = vinylCenterY + vinylRadius + (if (isStories) 64f else 36f)

        // 5. Track Title & Artist (Clean & High Contrast)
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT_PRIMARY
            textSize = if (isStories) width * 0.062f else width * 0.048f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = -0.02f
        }
        val artistPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_HYPER_VIOLET
            textSize = if (isStories) width * 0.038f else width * 0.032f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        val safeW = width - (padding * 2f)
        val titleText = ellipsize(track.title, titlePaint, safeW)
        val artistText = ellipsize(track.artist, artistPaint, safeW)

        canvas.drawText(titleText, vinylCenterX, curY, titlePaint)
        curY += (if (isStories) 50f else 40f)
        canvas.drawText(artistText, vinylCenterX, curY, artistPaint)

        curY += (if (isStories) 64f else 42f)

        // 6. Active Lyric Quote Card
        if (!currentLyricLine.isNullOrBlank()) {
            val quoteCardH = if (isStories) 320f else 200f
            val quoteRect = RectF(padding, curY, width - padding, curY + quoteCardH)

            val quoteBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    quoteRect.left, quoteRect.top,
                    quoteRect.right, quoteRect.bottom,
                    0xE0161426.toInt(), 0xE00C0A16.toInt(),
                    Shader.TileMode.CLAMP
                )
            }
            val quoteBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    quoteRect.left, quoteRect.top,
                    quoteRect.right, quoteRect.bottom,
                    COLOR_HYPER_VIOLET, COLOR_CYBER_CYAN,
                    Shader.TileMode.CLAMP
                )
                style = Paint.Style.STROKE
                strokeWidth = 2f
            }
            canvas.drawRoundRect(quoteRect, 24f, 24f, quoteBg)
            canvas.drawRoundRect(quoteRect, 24f, 24f, quoteBorder)

            // Neon quote glyph
            val glyphPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_CYBER_CYAN
                textSize = width * 0.065f
                typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            }
            canvas.drawText("“", quoteRect.left + 28f, quoteRect.top + 54f, glyphPaint)

            val lyricPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_TEXT_PRIMARY
                textSize = if (isStories) width * 0.044f else width * 0.035f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)
                setShadowLayer(14f, 0f, 0f, 0x66B388FF.toInt())
            }

            drawMultilineText(
                canvas,
                "«$currentLyricLine»",
                lyricPaint,
                quoteRect.left + 36f,
                quoteRect.top + 80f,
                (quoteRect.width() - 72f).toInt(),
                if (isStories) 4 else 3
            )
        }

        // 7. Footer Branding
        drawFooter(canvas, width, height)

        return bitmap
    }

    private fun drawAlbumCoverCollage(
        canvas: Canvas,
        width: Int,
        height: Int,
        providedBitmaps: List<Bitmap>,
        stats: WrappedStats,
        posterStyle: PosterStyle
    ) {
        val covers = mutableListOf<Bitmap>()
        covers.addAll(providedBitmaps.filter { !it.isRecycled })

        // Try load from topTracks albumArtUri if not enough
        for (track in stats.topTracks) {
            if (covers.size >= 24) break
            val uri = track.albumArtUri ?: continue
            try {
                val path = uri.removePrefix("file://")
                val f = File(path)
                if (f.exists() && f.length() > 0L) {
                    val bmp = BitmapFactory.decodeFile(f.absolutePath)
                    if (bmp != null) covers.add(bmp)
                }
            } catch (_: Exception) {}
        }

        // Procedural fallbacks if covers < 24 (colors follow the poster style)
        val styleColors = listOf(posterStyle.c1, posterStyle.c2, posterStyle.c3, posterStyle.c4)
        val palette = styleColors.map { Pair(it, darken(it)) }

        var palIdx = 0
        while (covers.size < 24) {
            val trackName = stats.topTracks.getOrNull(covers.size % stats.topTracks.size.coerceAtLeast(1))?.title
                ?: "TRACK #${covers.size + 1}"
            val pair = palette[palIdx % palette.size]
            palIdx++
            val dummyBmp = createProceduralCover(trackName, pair.first, pair.second)
            covers.add(dummyBmp)
        }

        if (posterStyle.mode == CollageMode.BLUR) {
            // One huge softly blurred cover: tiny bitmap stretched with bilinear filtering
            val tiny = Bitmap.createScaledBitmap(covers[0], 24, 24, true)
            val blurPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                alpha = posterStyle.collageAlpha
            }
            val side = maxOf(width, height) * 1.25f
            val left = (width - side) / 2f
            val top = (height - side) / 2f
            canvas.drawBitmap(tiny, null, RectF(left, top, left + side, top + side), blurPaint)
        }

        if (posterStyle.mode == CollageMode.TILTED) {
            canvas.save()
            canvas.rotate(posterStyle.tilt, width * 0.5f, height * 0.5f)

            val tileW = width * posterStyle.tileScale
            val tileH = tileW
            val gap = width * 0.024f
            // Enough tiles to cover the whole rotated canvas for any tile size
            val cols = Math.ceil((width * 1.8f / (tileW + gap)).toDouble()).toInt() + 1
            val rows = Math.ceil((height * 1.8f / (tileH + gap)).toDouble()).toInt() + 1
            val startX = -width * 0.4f
            val startY = -height * 0.3f

            val tilePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                alpha = posterStyle.collageAlpha
            }
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x24FFFFFF
                style = Paint.Style.STROKE
                strokeWidth = 1.5f
            }

            var coverIdx = 0
            for (r in 0 until rows) {
                val offsetX = if (r % 2 == 1) tileW * 0.5f else 0f
                for (c in 0 until cols) {
                    val x = startX + c * (tileW + gap) + offsetX
                    val y = startY + r * (tileH + gap)
                    val rect = RectF(x, y, x + tileW, y + tileH)

                    val bmp = covers[coverIdx % covers.size]
                    coverIdx++

                    val path = Path().apply { addRoundRect(rect, 22f, 22f, Path.Direction.CW) }
                    canvas.save()
                    canvas.clipPath(path)
                    canvas.drawBitmap(bmp, null, rect, tilePaint)
                    canvas.restore()
                    canvas.drawRoundRect(rect, 22f, 22f, borderPaint)
                }
            }
            canvas.restore()
        }

        // Cinematic Dark Vignette & Gradient Overlay
        // Softened so background collage art and specular glow remain visible throughout
        val vignette = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                width * 0.5f,
                height * 0.40f,
                width * 0.88f,
                intArrayOf(0x20000000.toInt(), 0x88000000.toInt(), 0xCC000000.toInt()),
                floatArrayOf(0f, 0.60f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), vignette)

        val bottomGrad = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, height * 0.20f,
                0f, height.toFloat(),
                intArrayOf(0x10000000.toInt(), 0x77000000.toInt(), 0xCC000000.toInt()),
                floatArrayOf(0f, 0.50f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bottomGrad)
    }

    private fun createProceduralCover(label: String, color1: Int, color2: Int): Bitmap {
        val size = 256
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, size.toFloat(), size.toFloat(), color1, color2, Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 0f, size.toFloat(), size.toFloat(), p)

        // Vinyl concentric arc rings
        val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x30FFFFFF
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        for (r in listOf(40f, 75f, 110f)) {
            c.drawCircle(size * 0.5f, size * 0.5f, r, arcPaint)
        }

        // Bold lettermark
        val letter = label.take(2).uppercase()
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x77FFFFFF.toInt()
            textSize = 68f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        c.drawText(letter, size * 0.5f, size * 0.58f, textPaint)
        return bmp
    }

    private fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha shl 24)

    private fun darken(color: Int, keep: Float = 0.18f): Int {
        val r = ((color shr 16 and 0xFF) * keep).toInt()
        val g = ((color shr 8 and 0xFF) * keep).toInt()
        val b = ((color and 0xFF) * keep).toInt()
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun drawAtmosphericGlow(canvas: Canvas, width: Int, height: Int, style: PosterStyle) {
        val m = if (style.glowMirror) -1f else 1f
        fun x(f: Float) = if (m > 0f) width * f else width * (1f - f)
        fun spot(fx: Float, fy: Float, fr: Float, color: Int, a1: Int, a2: Int) {
            val cx = x(fx)
            val cy = height * fy
            val r = width * fr
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    cx, cy, r,
                    intArrayOf(withAlpha(color, a1), withAlpha(color, a2), 0x00000000),
                    floatArrayOf(0f, 0.5f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawCircle(cx, cy, r, paint)
        }
        spot(0.85f, 0.20f, 0.70f, style.c1, 0x38, 0x12)
        spot(0.15f, 0.55f, 0.65f, style.c2, 0x28, 0x08)
        spot(0.25f, 0.82f, 0.55f, style.c3, 0x33, 0x12)
        spot(0.80f, 0.88f, 0.50f, style.c2, 0x2E, 0x0A)
    }
    private fun drawBackgroundGrooveArcs(canvas: Canvas, width: Int, height: Int) {
        val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x14FFFFFF
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }
        val cx = width * 0.95f
        val cy = height * 0.15f
        for (r in listOf(200f, 320f, 460f, 620f)) {
            canvas.drawCircle(cx, cy, r, arcPaint)
        }
    }

    private fun drawHighFidelityVinylDisc(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        artBitmap: Bitmap?
    ) {
        // Outer vinyl body
        val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx, cy, radius,
                intArrayOf(0xFF1E202B.toInt(), 0xFF101117.toInt(), 0xFF050608.toInt()),
                floatArrayOf(0.4f, 0.82f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(cx, cy, radius, basePaint)

        // Specular sheen (simulating vinyl light reflection arcs)
        val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = SweepGradient(
                cx, cy,
                intArrayOf(
                    0x00FFFFFF, 0x1FFFFFFF, 0x00FFFFFF,
                    0x00FFFFFF, 0x1FFFFFFF, 0x00FFFFFF
                ),
                floatArrayOf(0f, 0.25f, 0.50f, 0.55f, 0.75f, 1f)
            )
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, cy, radius, sheenPaint)

        // Micro concentric grooves
        val groovePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x22FFFFFF
            style = Paint.Style.STROKE
            strokeWidth = 1.2f
        }
        val steps = 9
        for (i in 1..steps) {
            val r = radius * (0.46f + (i * 0.055f))
            canvas.drawCircle(cx, cy, r, groovePaint)
        }

        // Center Album Art circle
        val centerRadius = radius * 0.44f
        val centerRect = RectF(cx - centerRadius, cy - centerRadius, cx + centerRadius, cy + centerRadius)

        if (artBitmap != null) {
            drawCircularBitmap(canvas, artBitmap, centerRect)
        } else {
            val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    centerRect.left, centerRect.top,
                    centerRect.right, centerRect.bottom,
                    COLOR_HYPER_VIOLET, COLOR_CYBER_CYAN,
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawCircle(cx, cy, centerRadius, placeholderPaint)
        }

        // Glowing center border
        val centerBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xEEF4F4F8.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
        }
        canvas.drawCircle(cx, cy, centerRadius, centerBorderPaint)

        // Spindle center hole
        val spindleHole = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_AMOLED_BLACK
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, cy, radius * 0.075f, spindleHole)

        val spindleNub = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFA0A5B8.toInt()
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, cy, radius * 0.025f, spindleNub)
    }

    private fun drawFooter(canvas: Canvas, width: Int, height: Int) {
        val footerY = height - (width * 0.065f)

        // High-precision neon hairline
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                width * 0.12f, footerY,
                width * 0.88f, footerY,
                COLOR_HYPER_VIOLET, COLOR_CYBER_CYAN,
                Shader.TileMode.CLAMP
            )
            strokeWidth = 2f
        }
        canvas.drawLine(width * 0.12f, footerY, width * 0.88f, footerY, linePaint)

        // Minimalist Branding Text
        val brandPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT_MUTED
            textSize = width * 0.023f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            letterSpacing = 0.16f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("MUSIC TRACKER // VERIFIED AUDIO REPLAY // 2026", width / 2f, footerY + 32f, brandPaint)
    }

    private fun drawRoundedBitmap(canvas: Canvas, bitmap: Bitmap, rect: RectF, radius: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val path = Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(path)
        canvas.drawBitmap(bitmap, null, rect, paint)
        canvas.restore()

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_BORDER_SUBTLE
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        canvas.drawRoundRect(rect, radius, radius, borderPaint)
    }

    private fun drawCircularBitmap(canvas: Canvas, bitmap: Bitmap, rect: RectF) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val path = Path().apply { addCircle(rect.centerX(), rect.centerY(), rect.width() / 2f, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(path)
        canvas.drawBitmap(bitmap, null, rect, paint)
        canvas.restore()
    }

    private fun drawPlaceholderArt(canvas: Canvas, rect: RectF, radius: Float) {
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                rect.left, rect.top, rect.right, rect.bottom,
                COLOR_SURFACE_2, COLOR_SURFACE_1,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(rect, radius, radius, bgPaint)

        val notePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT_MUTED
            textSize = rect.height() * 0.45f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("♫", rect.centerX(), rect.centerY() + (notePaint.textSize * 0.35f), notePaint)
    }

    private fun drawMultilineText(
        canvas: Canvas,
        text: String,
        paint: TextPaint,
        x: Float,
        y: Float,
        width: Int,
        maxLines: Int
    ) {
        if (width <= 0) return
        val layout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.25f)
                .setMaxLines(maxLines)
                .build()
        } else {
            @Suppress("DEPRECATION")
            StaticLayout(text, paint, width, Layout.Alignment.ALIGN_NORMAL, 1.25f, 0f, false)
        }

        canvas.save()
        canvas.translate(x, y)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun ellipsize(text: String, paint: TextPaint, maxWidth: Float): String {
        if (maxWidth <= 0f) return text
        if (paint.measureText(text) <= maxWidth) return text
        val ellipsis = "…"
        val ellW = paint.measureText(ellipsis)
        val avail = maxWidth - ellW
        var len = text.length
        while (len > 0 && paint.measureText(text.substring(0, len)) > avail) {
            len--
        }
        return if (len > 0) text.substring(0, len) + ellipsis else ellipsis
    }

    private fun pluralize(count: Int, one: String, few: String, many: String): String {
        val n = kotlin.math.abs(count) % 100
        val n1 = n % 10
        val word = when {
            n in 11..19 -> many
            n1 == 1 -> one
            n1 in 2..4 -> few
            else -> many
        }
        return "$count $word"
    }

    private fun pluralizeWordOnly(count: Int, one: String, few: String, many: String): String {
        val n = kotlin.math.abs(count) % 100
        val n1 = n % 10
        return when {
            n in 11..19 -> many
            n1 == 1 -> one
            n1 in 2..4 -> few
            else -> many
        }
    }
}
