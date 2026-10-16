package com.lirix.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lirix.app.analytics.*
import com.lirix.app.feature.share.*
import com.lirix.app.storage.TrackEntity
import com.lirix.app.ui.theme.AppThemeType
import com.lirix.app.ui.theme.ColorPalettes
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ThemedPosterRenderTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private fun stats() = WrappedStats(
        totalListeningTimeMs = 7_920_000L, uniqueTracksCount = 42, uniqueArtistsCount = 18,
        topArtists = emptyList(), topTracks = listOf(
            TopTrackItem("Письма на полях", "Тихий город", 12, 600000, "a"),
            TopTrackItem("Всё ещё здесь", "Комната с окнами", 8, 400000, "b"),
            TopTrackItem("Дорога домой", "Поздний рейс", 5, 300000, "c")
        ), timeOfDayDistribution = emptyMap(), archetype = ListeningArchetype.CASUAL_LISTENER
    )

    @Test fun everyThemeAndFormatUsesExactActiveBackgroundForBothExports() {
        for (type in AppThemeType.entries) for (format in ShareCardFormat.entries) {
            val theme = PosterThemeSnapshot.fromColors(ColorPalettes.getTokens(type))
            val wrapped = ShareCardGenerator.generateWrappedCard(
                context, stats(), AnalyticsTimeframe.MONTH, format, theme = theme
            )
            assertEquals(theme.background, wrapped.getPixel(0, 0))
            assertEquals(format.width, wrapped.width)
            assertEquals(format.height, wrapped.height)
            preview(wrapped, "${type.id}-${format.name}-wrapped")
            val nowPlaying = ShareCardGenerator.generateNowPlayingCard(
                context, TrackEntity("a", "Письма на полях", "Тихий город", sourcePackage = "example.player"),
                "Демонстрационный текст для проверки оформления", format, theme = theme
            )
            assertEquals(theme.background, nowPlaying.getPixel(0, 0))
            preview(nowPlaying, "${type.id}-${format.name}-track")
            wrapped.recycle()
            nowPlaying.recycle()
        }
    }

    @Test fun missingFirstCoverCannotShiftSecondCoverToFirstTrack() {
        val theme = PosterThemeSnapshot.fromColors(ColorPalettes.getTokens(AppThemeType.GRAPHITE_CHALK))
        val red = Color.rgb(239, 17, 34)
        val cover = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888).apply { eraseColor(red) }
        val result = ShareCardGenerator.generateWrappedCard(
            context, stats(), AnalyticsTimeframe.MONTH, ShareCardFormat.STORIES_9_16,
            theme = theme, artworkByTrackKey = mapOf("a" to null, "b" to cover)
        )
        val firstEmptyCoverY = (0 until result.height).first { result.getPixel(80, it) == theme.surface }
        val firstRedCoverY = (0 until result.height).first { result.getPixel(80, it) == red }
        assertTrue("The second track's artwork must remain in its own row", firstRedCoverY >= firstEmptyCoverY + 120)
        cover.recycle()
        result.recycle()
    }

    @Test fun emptyDataAndLongTextRemainDeterministic() {
        val theme = PosterThemeSnapshot.fromColors(ColorPalettes.getTokens(AppThemeType.PAPER_INK))
        val track = TrackEntity("long", "Очень длинное название песни ".repeat(12),
            "Очень длинное имя исполнителя ".repeat(12), sourcePackage = "example.player")
        val a = ShareCardGenerator.generateNowPlayingCard(context, track, "Строка\n".repeat(30), ShareCardFormat.SQUARE_1_1, theme = theme)
        val b = ShareCardGenerator.generateNowPlayingCard(context, track, "Строка\n".repeat(30), ShareCardFormat.SQUARE_1_1, theme = theme)
        assertTrue(a.sameAs(b))
        preview(a, "paper-ink-long-text")
        val empty = stats().copy(totalListeningTimeMs = 0, uniqueTracksCount = 0, uniqueArtistsCount = 0, topTracks = emptyList())
        val card = ShareCardGenerator.generateWrappedCard(context, empty, AnalyticsTimeframe.TODAY, ShareCardFormat.SQUARE_1_1, theme = theme)
        assertEquals(theme.background, card.getPixel(0, 0))
        preview(card, "paper-ink-empty")
        a.recycle(); b.recycle(); card.recycle()
    }

    private fun preview(bitmap: Bitmap, name: String) {
        val target = System.getenv("LIRIX_POSTER_PREVIEW_DIR") ?: return
        val directory = File(target).apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
