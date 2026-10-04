package com.eventengine.app

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eventengine.app.analytics.AnalyticsTimeframe
import com.eventengine.app.analytics.DayOfWeekActivity
import com.eventengine.app.analytics.ListeningArchetype
import com.eventengine.app.analytics.TimeOfDaySlot
import com.eventengine.app.analytics.TopArtistItem
import com.eventengine.app.analytics.TopObsessionItem
import com.eventengine.app.analytics.TopTrackItem
import com.eventengine.app.analytics.WrappedStats
import com.eventengine.app.feature.share.ShareCardFormat
import com.eventengine.app.feature.share.ShareCardGenerator
import com.eventengine.app.feature.share.ShareManager
import com.eventengine.app.storage.TrackEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.io.File

/**
 * TDD Unit-tests for ShareCardGenerator and ShareManager (TEST-SHR-01 / TASK-SHR-01).
 * Validates:
 * 1. Wrapped card rendering in STORIES_9_16 (1080x1920) and SQUARE_1_1 (1080x1080).
 * 2. Wrapped card rendering with and without obsessionBitmap (fallback).
 * 3. NowPlaying card rendering in STORIES_9_16 and SQUARE_1_1.
 * 4. NowPlaying card rendering without albumArtBitmap and without currentLyricLine.
 * 5. ShareManager saving PNG to cacheDir/shares/ and firing Intent.ACTION_SEND with FLAG_GRANT_READ_URI_PERMISSION.
 */
@RunWith(AndroidJUnit4::class)
class ShareCardGeneratorTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        val sharesDir = File(context.cacheDir, "shares")
        if (sharesDir.exists()) {
            sharesDir.deleteRecursively()
        }
    }

    private fun createSampleWrappedStats(): WrappedStats {
        return WrappedStats(
            totalListeningTimeMs = 7_200_000L,
            uniqueTracksCount = 42,
            uniqueArtistsCount = 18,
            repeatRatio = 2.4f,
            lyricsCoverageRatio = 0.65f,
            topArtists = listOf(
                TopArtistItem(artist = "The Weeknd", playCount = 25, durationMs = 3_600_000L),
                TopArtistItem(artist = "Linkin Park", playCount = 15, durationMs = 2_100_000L)
            ),
            topTracks = listOf(
                TopTrackItem(title = "Starboy", artist = "The Weeknd", playCount = 12, durationMs = 1_800_000L),
                TopTrackItem(title = "Numb", artist = "Linkin Park", playCount = 8, durationMs = 1_200_000L),
                TopTrackItem(title = "In The End", artist = "Linkin Park", playCount = 6, durationMs = 1_000_000L)
            ),
            topObsession = TopObsessionItem(
                trackKey = "key_starboy",
                title = "Starboy",
                artist = "The Weeknd",
                albumArtUri = null,
                playCountInPeriod = 12,
                durationMsInPeriod = 1_800_000L
            ),
            timeOfDayDistribution = mapOf(
                TimeOfDaySlot.NIGHT to 0.45f,
                TimeOfDaySlot.MORNING to 0.15f,
                TimeOfDaySlot.AFTERNOON to 0.20f,
                TimeOfDaySlot.EVENING to 0.20f
            ),
            weeklyActivity = listOf(
                DayOfWeekActivity("Пн", 5, 600_000L),
                DayOfWeekActivity("Вт", 8, 1_200_000L),
                DayOfWeekActivity("Ср", 4, 500_000L),
                DayOfWeekActivity("Чт", 6, 900_000L),
                DayOfWeekActivity("Пт", 12, 2_000_000L),
                DayOfWeekActivity("Сб", 10, 1_500_000L),
                DayOfWeekActivity("Вс", 3, 500_000L)
            ),
            archetype = ListeningArchetype.NIGHT_OWL
        )
    }

    @Test
    fun testGenerateWrappedCard_storiesFormat_returnsExactDimensions() {
        val stats = createSampleWrappedStats()
        val obsessionBitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)

        val card = ShareCardGenerator.generateWrappedCard(
            context = context,
            stats = stats,
            timeframe = AnalyticsTimeframe.MONTH,
            format = ShareCardFormat.STORIES_9_16,
            obsessionBitmap = obsessionBitmap
        )

        assertNotNull("Generated card must not be null", card)
        assertEquals("Stories width must be 1080px", 1080, card.width)
        assertEquals("Stories height must be 1920px", 1920, card.height)
    }

    @Test
    fun testGenerateWrappedCard_squareFormat_returnsExactDimensions_andHandlesNullObsessionBitmap() {
        val stats = createSampleWrappedStats().copy(topObsession = null)

        val card = ShareCardGenerator.generateWrappedCard(
            context = context,
            stats = stats,
            timeframe = AnalyticsTimeframe.ALL_TIME,
            format = ShareCardFormat.SQUARE_1_1,
            obsessionBitmap = null
        )

        assertNotNull("Generated card must not be null", card)
        assertEquals("Square width must be 1080px", 1080, card.width)
        assertEquals("Square height must be 1080px", 1080, card.height)
    }

    @Test
    fun testGenerateNowPlayingCard_storiesFormat_rendersWithFullData() {
        val track = TrackEntity(
            trackKey = "np_key",
            title = "Blinding Lights",
            artist = "The Weeknd",
            sourcePackage = "com.spotify.music"
        )
        val lyricLine = "I said, ooh, I'm blinded by the lights"
        val albumArt = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888)

        val card = ShareCardGenerator.generateNowPlayingCard(
            context = context,
            track = track,
            currentLyricLine = lyricLine,
            format = ShareCardFormat.STORIES_9_16,
            albumArtBitmap = albumArt
        )

        assertNotNull("NowPlaying card must not be null", card)
        assertEquals(1080, card.width)
        assertEquals(1920, card.height)
    }

    @Test
    fun testGenerateNowPlayingCard_squareFormat_handlesNullArtAndNullLyrics() {
        val track = TrackEntity(
            trackKey = "np_key_fallback",
            title = "Midnight City",
            artist = "M83",
            sourcePackage = "ru.yandex.music"
        )

        val card = ShareCardGenerator.generateNowPlayingCard(
            context = context,
            track = track,
            currentLyricLine = null,
            format = ShareCardFormat.SQUARE_1_1,
            albumArtBitmap = null
        )

        assertNotNull("NowPlaying card must gracefully generate without art and lyrics", card)
        assertEquals(1080, card.width)
        assertEquals(1080, card.height)
    }

    @Test
    fun testShareManager_savesFileToCache_andDispatchesSendIntent() = runBlocking {
        val bitmap = Bitmap.createBitmap(500, 500, Bitmap.Config.ARGB_8888)

        ShareManager.shareBitmap(
            context = context,
            bitmap = bitmap,
            chooserTitle = "Поделиться тестом"
        )

        // Verify file written to cache/shares
        val sharesDir = File(context.cacheDir, "shares")
        assertTrue("Shares directory must exist", sharesDir.exists())
        val files = sharesDir.listFiles()
        assertNotNull("Shares dir files list must not be null", files)
        assertTrue("At least one share file must be saved", files!!.isNotEmpty())
        val savedFile = files.first()
        assertTrue("File must have .png extension", savedFile.name.endsWith(".png"))
        assertTrue("File size must be greater than zero", savedFile.length() > 0)

        // Verify Intent dispatched via Robolectric
        val shadowApp = shadowOf(context as android.app.Application)
        val startedIntent = shadowApp.nextStartedActivity
        assertNotNull("An intent must be fired by shareBitmap", startedIntent)
        assertEquals("Fired intent must be Intent.CHOOSER", Intent.ACTION_CHOOSER, startedIntent.action)

        val targetIntent = startedIntent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        assertNotNull("Chooser must wrap a target intent", targetIntent)
        assertEquals("Target intent action must be ACTION_SEND", Intent.ACTION_SEND, targetIntent!!.action)
        assertEquals("Target intent type must be image/png", "image/png", targetIntent.type)
        assertTrue("Flag FLAG_GRANT_READ_URI_PERMISSION must be present",
            (targetIntent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)
    }

    @Test
    fun testExportPreviewImagesToDisk() {
        val stats = createSampleWrappedStats()
        val obsessionBitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888).apply {
            val c = Canvas(this)
            val p = Paint().apply { color = 0xFF7C4DFF.toInt() }
            c.drawRect(0f, 0f, 256f, 256f, p)
        }

        val wrappedStories = ShareCardGenerator.generateWrappedCard(
            context, stats, AnalyticsTimeframe.MONTH, ShareCardFormat.STORIES_9_16, obsessionBitmap
        )
        val wrappedSquare = ShareCardGenerator.generateWrappedCard(
            context, stats, AnalyticsTimeframe.MONTH, ShareCardFormat.SQUARE_1_1, obsessionBitmap
        )
        val track = TrackEntity(
            trackKey = "preview_track",
            title = "Starboy",
            artist = "The Weeknd ft. Daft Punk",
            album = "Starboy",
            sourcePackage = "com.spotify.music"
        )
        val nowPlayingStories = ShareCardGenerator.generateNowPlayingCard(
            context, track, "I'm tryna put you in the worst mood, ah", ShareCardFormat.STORIES_9_16, obsessionBitmap
        )

        val outDirs = listOf(
            File("C:/Users/DaniilTuT/.gemini/antigravity/brain/32389d24-0cd3-4c9a-aab7-af3d631a6961"),
            File("C:/Users/DaniilTuT/.gemini/antigravity/brain/3b10c272-21fc-40f8-bf03-67db8f3f2c49")
        )
        for (outDir in outDirs) {
            outDir.mkdirs()
            File(outDir, "wrapped_stories_preview.png").outputStream().use {
                wrappedStories.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            File(outDir, "wrapped_square_preview.png").outputStream().use {
                wrappedSquare.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            File(outDir, "now_playing_stories_preview.png").outputStream().use {
                nowPlayingStories.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }

    @Test
    fun testAllStylesAndSections_renderExactDimensions() {
        val stats = createSampleWrappedStats()
        for (style in com.eventengine.app.feature.share.PosterStyle.values()) {
            for (format in ShareCardFormat.values()) {
                val card = ShareCardGenerator.generateWrappedCard(
                    context = context,
                    stats = stats,
                    timeframe = AnalyticsTimeframe.ALL_TIME,
                    format = format,
                    posterStyle = style
                )
                assertEquals(format.width, card.width)
                assertEquals(format.height, card.height)
            }
        }
        for (section in com.eventengine.app.feature.share.WrappedShareSection.values()) {
            for (format in ShareCardFormat.values()) {
                val card = ShareCardGenerator.generateWrappedCard(
                    context = context,
                    stats = stats,
                    timeframe = AnalyticsTimeframe.MONTH,
                    format = format,
                    section = section,
                    posterStyle = com.eventengine.app.feature.share.PosterStyle.random()
                )
                assertEquals(format.width, card.width)
                assertEquals(format.height, card.height)
            }
        }
    }
}