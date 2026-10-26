package com.lirix.app.ui

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.lirix.app.R
import com.lirix.app.feature.*
import com.lirix.app.feature.lyrics.*
import com.lirix.app.storage.*
import com.lirix.app.ui.components.*
import com.lirix.app.ui.i18n.*
import com.lirix.app.ui.theme.LirixTheme
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.Locale

class OfflineLookupRetryUiTest {
    private val ui = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val rules: org.junit.rules.TestRule = org.junit.rules.RuleChain.outerRule(ForegroundComposeHostRule()).around(ui)
    @Test fun russianLargePortraitRetryReachesContent() = verify(AppLanguage.RUSSIAN, 2f, false)
    @Test fun russianLargeLandscapeRetryReachesContent() = verify(AppLanguage.RUSSIAN, 2f, true)
    @Test fun englishLargePortraitRetryReachesContent() = verify(AppLanguage.ENGLISH, 2f, false)
    @Test fun englishLargeLandscapeRetryReachesContent() = verify(AppLanguage.ENGLISH, 2f, true)
    @Test fun russianNormalPortraitRetryReachesContent() = verify(AppLanguage.RUSSIAN, 1f, false)
    @Test fun russianNormalLandscapeRetryReachesContent() = verify(AppLanguage.RUSSIAN, 1f, true)
    @Test fun englishNormalPortraitRetryReachesContent() = verify(AppLanguage.ENGLISH, 1f, false)
    @Test fun englishNormalLandscapeRetryReachesContent() = verify(AppLanguage.ENGLISH, 1f, true)

    /** A rule-installed composition belongs to one Activity instance, not its orientation replacement. */
    private fun prepareHost(language: AppLanguage, requested: Int, orientation: Int) {
        ui.runOnUiThread {
            LanguageManager.setLanguage(ui.activity, language)
            ui.activity.requestedOrientation = requested
        }
        fun configured(): Boolean {
            val activity = ui.activity
            val configuration = activity.resources.configuration
            return !activity.isDestroyed && !activity.isFinishing &&
                activity.lifecycle.currentState == androidx.lifecycle.Lifecycle.State.RESUMED &&
                configuration.orientation == orientation && configuration.locales[0].language == language.code &&
                LanguageManager.getCurrentLanguage(activity) == language
        }
        ui.waitUntil(15_000) { configured() }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        // Finish configuration recreation before setContent, including framework app-locale updates.
        ui.activityRule.scenario.recreate()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        ui.waitUntil(15_000) { configured() }
    }

    private fun verify(language: AppLanguage, scale: Float, landscape: Boolean) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val original = LanguageManager.getCurrentLanguage(ui.activity)
        val originalOrientation = ui.activity.requestedOrientation
        val orientation = if (landscape) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
        val requested = if (landscape) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        val trackKey = "${language.code}-$scale-$orientation"
        val db = Room.inMemoryDatabaseBuilder(ui.activity.applicationContext, AppDatabase::class.java).build()
        val jobs = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val showContent = mutableStateOf(true)
        val network = mutableStateOf(LookupNetworkSnapshot(false, 0))
        val successful = java.util.concurrent.atomic.AtomicBoolean(false)
        val requests = java.util.concurrent.atomic.AtomicInteger(0)
        val body = instrumentation.context.assets.open("lookup/sakura-1888.json").bufferedReader().use { it.readText() }
        val expected = org.json.JSONObject(body).getString("plainLyrics")
        val provider = AggregatedLyricsProvider(lrcLibProvider = LrcLibLyricsProvider {
            requests.incrementAndGet(); if (successful.get()) body else null
        }, fallbackScraper = null)
        val coordinator = AutomaticLookupCoordinator(object : AutomaticLookupStore {
            override suspend fun track(key: String) = db.musicDao().getTrackByKey(key)
            override suspend fun cache(key: String) = db.lyricsDao().getLyrics(key)
            override suspend fun rejected(key: String) = db.lyricsDao().getRejectedSourceIds(key).toSet()
            override suspend fun merge(key: String, candidate: AutomaticLookupCandidate) = db.lyricsDao().fillMissingAutomaticLookup(key, candidate)
        }, provider::acquireAutomatic, jobs)
        fun label(id: Int) = ui.activity.createConfigurationContext(Configuration(ui.activity.resources.configuration).apply { setLocale(Locale(language.code)) }).getString(id)
        try {
            prepareHost(language, requested, orientation)
            runBlocking { db.musicDao().insertOrUpdateTrack(TrackEntity(trackKey, "Fixture song", "Artist", sourcePackage = "test", userNotes = "keep note")) }
            // Exactly one content installation, after the actual host configuration has settled.
            ui.setContent { if (showContent.value) LocalizedAppContent {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                    LirixTheme {
                        val scope = rememberCoroutineScope()
                        val session = remember { ForegroundLookupRecovery(trackKey, LookupTarget.LYRICS, coordinator, scope) }
                        val phase by session.phase.collectAsState()
                        var cached by remember { mutableStateOf<LyricsCacheEntity?>(null) }
                        LaunchedEffect(Unit) { db.lyricsDao().observeLyrics(trackKey).collect { cached = it } }
                        LaunchedEffect(network.value) { session.onNetwork(network.value) }
                        DisposableEffect(Unit) { onDispose { session.dispose() } }
                        Box(Modifier.widthIn(max = 320.dp).fillMaxHeight().testTag("retry-viewport")) {
                            ProductionLookupReader(NowPlayingMode.KARAOKE, cached,
                                TrackEntity(trackKey, "Fixture song", "Artist", sourcePackage = "test", userNotes = "keep note"),
                                phase, session::retry)
                        }
                    }
                }
            } }
            assertEquals(orientation, ui.activity.resources.configuration.orientation)
            ui.waitUntil(10_000) { ui.onAllNodesWithTag("lookup-retry").fetchSemanticsNodes().isNotEmpty() }
            ui.onNodeWithTag("lookup-status").assertTextEquals(label(R.string.lookup_waiting_network))
            assertEquals(0, requests.get())
            if (scale == 2f) {
                val file = java.io.File(instrumentation.targetContext.cacheDir,
                    "lookup-waiting-${language.code}-${if (landscape) "landscape" else "portrait"}-font2.png")
                val image = ui.onNodeWithTag("retry-viewport").captureToImage().asAndroidBitmap()
                file.outputStream().use { assertTrue(image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)) }
            }
            ui.runOnIdle { network.value = LookupNetworkSnapshot(true, 1) }
            ui.waitUntil(10_000) { ui.onAllNodesWithText(label(R.string.lookup_incomplete)).fetchSemanticsNodes().isNotEmpty() }
            assertTrue(requests.get() > 0)
            ui.onNodeWithTag("lookup-status").assertTextEquals(label(R.string.lookup_incomplete))
            val retry = ui.onNodeWithTag("lookup-retry").performScrollTo().assertIsDisplayed()
            retry.assertTextContains(label(R.string.lookup_retry), substring = true)
            val rect = retry.getUnclippedBoundsInRoot()
            val viewport = ui.onNodeWithTag("retry-viewport").getUnclippedBoundsInRoot()
            assertTrue(rect.bottom - rect.top >= 48.dp); assertTrue(rect.bottom <= viewport.bottom)
            successful.set(true); retry.performClick()
            ui.waitUntil(10_000) { ui.onAllNodesWithText(expected.lineSequence().first(), substring = true).fetchSemanticsNodes().isNotEmpty() }
            ui.onNodeWithText(expected.lineSequence().first(), substring = true).assertExists()
            ui.onNodeWithTag("lookup-retry").assertDoesNotExist()
            val saved = runBlocking { db.musicDao().getTrackByKey(trackKey)!! }
            assertEquals(expected, saved.plainLyrics); assertEquals("keep note", saved.userNotes)
            assertTrue(requests.get() >= 2) // Source failure then actual successful retry, each via real provider flow.
            assertEquals(orientation, ui.activity.resources.configuration.orientation)
        } finally {
            // Dispose observers before closing Room; restore global settings only after assertions.
            ui.runOnUiThread { showContent.value = false }
            ui.waitForIdle()
            jobs.cancel(); db.close()
            ui.runOnUiThread {
                LanguageManager.setLanguage(ui.activity, original)
                ui.activity.requestedOrientation = originalOrientation
            }
        }
    }

    @Test fun productionReaderRetainsWordsAndNotesAndOffersOneScopedChordRetry() {
        val mode = mutableStateOf(NowPlayingMode.CHORDS)
        val phase = mutableStateOf(LookupPhase.Loading)
        val hasTrack = mutableStateOf(true)
        val draft = mutableStateOf("unsaved exact notes")
        val track = mutableStateOf(TrackEntity("visible", "Song", "Artist", sourcePackage = "test", plainLyrics = "Accepted words remain visible"))
        ui.setContent { LirixTheme {
            if (!hasTrack.value) EmptyPlayerStateView(PlayerEmptyStateType.NO_ACTIVE_PLAYER)
            else ProductionLookupReader(mode.value, null, track.value, phase.value, {}, draft.value, { draft.value = it })
        } }
        ui.onNodeWithText("Accepted words remain visible", substring = true).assertExists()
        ui.onAllNodesWithTag("lookup-retry").assertCountEquals(1)
        ui.onNodeWithTag("lookup-retry").assertIsNotEnabled()
        ui.runOnIdle { phase.value = LookupPhase.Incomplete }
        ui.onNodeWithTag("lookup-retry").assertIsEnabled()
        ui.runOnIdle { mode.value = NowPlayingMode.NOTES }
        ui.onNodeWithTag("lookup-retry").assertDoesNotExist()
        ui.onNodeWithText("unsaved exact notes").assertExists()
        ui.runOnIdle { phase.value = LookupPhase.WaitingForNetwork }
        ui.onNodeWithText("unsaved exact notes").assertExists()
        ui.runOnIdle { mode.value = NowPlayingMode.KARAOKE; phase.value = LookupPhase.Available }
        ui.onNodeWithTag("lookup-retry").assertDoesNotExist()
        ui.runOnIdle { mode.value = NowPlayingMode.CHORDS; phase.value = LookupPhase.Loading; track.value = track.value.copy(plainLyrics = null, syncedLyrics = "[00:01.00]Retained synced words") }
        ui.onNodeWithText("Retained synced words", substring = true).assertExists()
        ui.onNodeWithTag("lookup-retry").assertIsNotEnabled()
        ui.runOnIdle { mode.value = NowPlayingMode.LYRICS; phase.value = LookupPhase.Incomplete; track.value = track.value.copy(plainLyrics = null, syncedLyrics = null) }
        ui.onAllNodesWithTag("lookup-retry").assertCountEquals(1)
        ui.onNodeWithText(ui.activity.getString(R.string.player_search_manually)).assertExists()
        ui.runOnIdle { hasTrack.value = false }
        ui.onNodeWithTag("lookup-retry").assertDoesNotExist()
    }
    @Test fun legacyNoteDuplicatesAreHiddenButIndependentWordsAndInlineChordsRemainVisible() {
        val notes = "Private legacy duplicate notes"
        val mode = mutableStateOf(NowPlayingMode.KARAOKE)
        val track = mutableStateOf(TrackEntity("legacy-ui", "Song", "Artist", sourcePackage = "test", userNotes = notes, plainLyrics = notes))
        val cache = mutableStateOf(LyricsCacheEntity("legacy-ui", notes, null, null, notes, "note:user"))
        ui.setContent { LirixTheme {
            ProductionLookupReader(mode.value, cache.value, track.value, LookupPhase.Incomplete, {})
        } }
        ui.onNodeWithText(notes, substring = true).assertDoesNotExist()
        ui.onNodeWithTag("lookup-retry").assertExists()
        ui.runOnIdle { track.value = track.value.copy(plainLyrics = "Independent actual words remain visible") }
        ui.onNodeWithText("Independent actual words remain visible", substring = true).assertExists()
        ui.onNodeWithTag("lookup-retry").assertDoesNotExist()
        for (emptyChords in listOf("", "   ")) {
            ui.runOnIdle {
                track.value = track.value.copy(plainLyrics = "Am G\nActual visible verse")
                cache.value = cache.value.copy(plainLyrics = "Am G\nActual visible verse", provider = LyricsSourceIds.LRCLIB, chordsAmDm = emptyChords)
                mode.value = NowPlayingMode.CHORDS
            }
            ui.onNodeWithText("Actual visible verse", substring = true).assertExists()
            ui.onNodeWithTag("lookup-retry").assertDoesNotExist()
        }
    }

}

@Composable
private fun ProductionLookupReader(mode: NowPlayingMode, cache: LyricsCacheEntity?, track: TrackEntity,
    phase: LookupPhase, retry: () -> Unit, notes: String = "keep note", onNotes: (String) -> Unit = {}) {
    RenderTeleprompterContent(selectedMode = mode, lyricsCache = cache, currentTrack = track,
        isLoadingLyrics = phase == LookupPhase.Loading, lookupPhase = phase, onRetryLookup = retry,
        playbackPositionMs = 0L,
        isPlaying = false, syncOffsetMs = 0L, canSeek = false, fontScale = 1f,
        isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE, isReducedMotion = true, onSeekTo = {}, onStartTapSync = {},
        onOpenTeachMode = {}, transposeSemitones = 0, onTransposeChange = {},
        chordsScrollState = rememberScrollState(), notesText = notes, onNotesChange = onNotes)
}
