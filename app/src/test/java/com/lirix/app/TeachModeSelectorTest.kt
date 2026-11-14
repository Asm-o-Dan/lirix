package com.lirix.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lirix.app.feature.lyrics.CustomRuleLyricsProvider
import com.lirix.app.storage.AppDatabase
import com.lirix.app.storage.CustomLyricsRuleEntity
import com.lirix.app.storage.LyricsAttribution
import com.lirix.app.storage.LyricsCacheEntity
import com.lirix.app.storage.TrackEntity
import com.lirix.app.storage.isManualLyricsProvider
import com.lirix.app.ui.TargetContentType
import com.lirix.app.ui.TeachModeBindingHelper
import com.lirix.app.ui.TeachRuleFactory
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * TeachMode rule JSON and Tier A binding (TASK-LYR-04-D, SPEC-TEACH-06 §5).
 * The TeachMode 2.0 tests exercise the production factory and binding transaction against a
 * real in-memory Room database instead of re-implementing them in the test.
 */
@RunWith(AndroidJUnit4::class)
class TeachModeSelectorTest {

    private lateinit var db: AppDatabase
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun test_generated_rule_json_is_valid_and_parsable() {
        val detectedDomain = "amalgama-lab.com"
        val selectedSelector = ".string_ru"

        val ruleJson = JSONObject().apply {
            put("domain", detectedDomain)
            put("name", detectedDomain)
            put("content", JSONObject().apply {
                put("selector", selectedSelector)
                put("stripSelectors", JSONArray(listOf("script", "style", ".ads", "button")))
            })
        }.toString()

        val config = CustomRuleLyricsProvider.parseConfig(ruleJson, detectedDomain, detectedDomain)

        assertEquals("amalgama-lab.com", config.domain)
        assertEquals("amalgama-lab.com", config.name)
        assertEquals(".string_ru", config.contentSelector)
        assertTrue(config.stripSelectors.contains("script"))
        assertTrue(config.stripSelectors.contains("style"))
        assertTrue(config.stripSelectors.contains(".ads"))
        assertTrue(config.stripSelectors.contains("button"))
    }

    @Test
    fun test_rule_entity_saved_with_correct_domain_and_priority() = runBlocking {
        val detectedDomain = "genius.com"
        val selectedSelector = "[data-lyrics-container='true']"
        val ruleId = "rule_genius_test"

        val ruleJson = JSONObject().apply {
            put("domain", detectedDomain)
            put("name", detectedDomain)
            put("content", JSONObject().apply {
                put("selector", selectedSelector)
                put("stripSelectors", JSONArray(listOf("script", "style")))
            })
        }.toString()

        val entity = CustomLyricsRuleEntity(
            id = ruleId,
            domain = detectedDomain,
            name = detectedDomain,
            ruleJson = ruleJson,
            isEnabled = true,
            priority = 100
        )

        db.lyricsDao().saveRule(entity)

        val loaded = db.lyricsDao().getRuleById(ruleId)
        assertNotNull(loaded)
        assertEquals(ruleId, loaded!!.id)
        assertEquals("genius.com", loaded.domain)
        assertTrue(loaded.isEnabled)
        assertEquals(100, loaded.priority)

        val activeRules = db.lyricsDao().getActiveRules()
        assertTrue(activeRules.any { it.id == ruleId })
    }

    // --- TeachMode 2.0 (SPEC-TEACH-06 §5) ---

    @Test
    fun rule_without_search_template_is_saved_as_non_executing_draft_even_if_activation_requested() = runBlocking {
        val entity = TeachRuleFactory.buildRule(
            id = "rule_draft",
            domain = "example-chords.com",
            contentSelector = "div.content-lyrics",
            stripSelectors = listOf(".ad-banner", "script"),
            contentType = TargetContentType.CHORDS,
            searchTemplate = "   ",
            resultSelector = null,
            activate = true
        )
        db.lyricsDao().saveRule(entity)

        assertFalse("A rule without a search step must not be enabled", entity.isEnabled)
        assertTrue(db.lyricsDao().getActiveRules().none { it.id == "rule_draft" })
        val config = CustomRuleLyricsProvider.parseConfig(entity.ruleJson, entity.domain, entity.name)
        assertEquals(2, config.schemaVersion)
        assertEquals("DRAFT", config.status)
        assertNull(config.searchUrlTemplate)
        assertEquals("div.content-lyrics", config.contentSelector)
        assertEquals("div.content-lyrics", config.chordsSelector)
        assertEquals("CHORDS", config.mode)
        assertEquals(listOf(".ad-banner", "script"), config.stripSelectors)
    }

    @Test
    fun validated_rule_with_template_is_active_and_round_trips_through_room() = runBlocking {
        val entity = TeachRuleFactory.buildRule(
            id = "rule_active",
            domain = "lyrics-hub.net",
            contentSelector = "div[data-lyrics-container=true]",
            stripSelectors = emptyList(),
            contentType = TargetContentType.LYRICS,
            searchTemplate = "https://lyrics-hub.net/search?q={query}",
            resultSelector = "ul.search-results a.result-card__link",
            activate = true
        )
        db.lyricsDao().saveRule(entity)

        val active = db.lyricsDao().getActiveRules().single { it.id == "rule_active" }
        val config = CustomRuleLyricsProvider.parseConfig(active.ruleJson, active.domain, active.name)
        assertEquals("ACTIVE", config.status)
        assertEquals("https://lyrics-hub.net/search?q={query}", config.searchUrlTemplate)
        assertEquals("ul.search-results a.result-card__link", config.searchResultSelector)
        assertEquals("LYRICS", config.mode)
        assertNull(config.chordsSelector)
    }

    @Test
    fun outdated_rule_check_uses_content_selector_before_legacy_selector() {
        val v2 = """{"schemaVersion":2,"content":{"contentSelector":"div.new-lyrics","selector":"div.legacy-lyrics"}}"""
        assertEquals("div.new-lyrics", CustomRuleLyricsProvider.targetSelector(CustomRuleLyricsProvider.parseConfig(v2, "a.com", "a")))
        val legacy = """{"content":{"selector":"itemprop=lyrics"}}"""
        assertEquals("[itemprop=\"lyrics\"]", CustomRuleLyricsProvider.targetSelector(CustomRuleLyricsProvider.parseConfig(legacy, "a.com", "a")))
    }

    @Test
    fun test_json_object_quote_prevents_js_injection() {
        val quoted = JSONObject.quote("div[class=\"lyrics\"]; alert(1);")
        assertTrue(quoted.startsWith("\""))
        assertTrue(quoted.endsWith("\""))
        assertTrue("Double quotes must be escaped", quoted.contains("\\\"lyrics\\\""))
        assertEquals("\"div[data-name='lyrics']\"", JSONObject.quote("div[data-name='lyrics']"))
    }

    private suspend fun seedTrack(trackKey: String, plain: String?, chords: String?): TrackEntity {
        val track = TrackEntity(
            trackKey = trackKey,
            title = "Song Title",
            artist = "Artist",
            plainLyrics = plain,
            syncedLyrics = "[00:01.00] Line 1",
            userNotes = "My note",
            sourcePackage = "com.spotify.music"
        )
        db.musicDao().insertOrUpdateTrack(track)
        db.lyricsDao().saveLyrics(
            LyricsCacheEntity(
                trackKey = trackKey,
                plainLyrics = plain,
                syncedLyricsLrc = track.syncedLyrics,
                chordsAmDm = chords,
                userNotes = track.userNotes,
                provider = "cache:initial",
                updatedAt = 1000L
            )
        )
        return track
    }

    @Test
    fun binding_chords_keeps_plain_lyrics_and_records_manual_attribution() = runBlocking {
        val track = seedTrack("k_chords", plain = "Original Verse 1\nOriginal Verse 2", chords = null)

        TeachModeBindingHelper.bindManualContentToTrack(
            db, track, "Am  C  G\nLa la la", TargetContentType.CHORDS, "www.amdm.ru", "https://amdm.ru/akkordi/x/", now = 5000L
        )

        assertEquals("Original Verse 1\nOriginal Verse 2", db.musicDao().getTrackByKey("k_chords")!!.plainLyrics)
        val cache = db.lyricsDao().getLyrics("k_chords")!!
        assertEquals("Original Verse 1\nOriginal Verse 2", cache.plainLyrics)
        assertEquals("Am  C  G\nLa la la", cache.chordsAmDm)
        assertEquals("[00:01.00] Line 1", cache.syncedLyricsLrc)
        assertEquals("My note", cache.userNotes)
        assertEquals("USER:teach:amdm.ru", cache.provider)
        assertEquals(5000L, cache.updatedAt)
        assertTrue(isManualLyricsProvider(cache.provider))
        val attribution = LyricsAttribution.decode(cache.sourceAttributionJson)
        assertEquals("USER:teach:amdm.ru", attribution.chords?.sourceId)
        assertEquals("https://amdm.ru/akkordi/x/", attribution.chords?.sourceUrl)
        assertNull(attribution.lyrics)
    }

    @Test
    fun binding_lyrics_keeps_stored_chords_and_never_parses_selection_as_chords() = runBlocking {
        val track = seedTrack("k_lyrics", plain = "old", chords = "Em  D\nold chords")
        // Text that looks like chord markup must still be stored only as plain lyrics.
        val selection = "Am                F\nТихо над рекою гаснут фонари"

        TeachModeBindingHelper.bindManualContentToTrack(db, track, selection, TargetContentType.LYRICS, "lyrics-hub.net")

        val cache = db.lyricsDao().getLyrics("k_lyrics")!!
        assertEquals(selection, cache.plainLyrics)
        assertEquals("Em  D\nold chords", cache.chordsAmDm)
        assertEquals(selection, db.musicDao().getTrackByKey("k_lyrics")!!.plainLyrics)
        assertNull("Old timing must not survive new manual words", cache.syncedLyricsLrc)
        assertNull(db.musicDao().getTrackByKey("k_lyrics")!!.syncedLyrics)

        val noChordsTrack = seedTrack("k_lyrics_nochords", plain = "old", chords = null)
        TeachModeBindingHelper.bindManualContentToTrack(db, noChordsTrack, selection, TargetContentType.LYRICS, "lyrics-hub.net")
        assertNull(db.lyricsDao().getLyrics("k_lyrics_nochords")!!.chordsAmDm)
    }

    @Test
    fun binding_writes_only_the_captured_track_and_inserts_it_when_missing() = runBlocking {
        val other = seedTrack("k_other", plain = "Other song text", chords = null)
        val captured = TrackEntity(trackKey = "k_new", title = "New", artist = "A", sourcePackage = "p")

        TeachModeBindingHelper.bindManualContentToTrack(db, captured, "Line one\nLine two", TargetContentType.LYRICS, "www.google.com")

        assertEquals("Line one\nLine two", db.musicDao().getTrackByKey("k_new")!!.plainLyrics)
        assertEquals("USER:teach:manual", db.lyricsDao().getLyrics("k_new")!!.provider)
        assertEquals("Other song text", db.musicDao().getTrackByKey(other.trackKey)!!.plainLyrics)
        assertEquals("cache:initial", db.lyricsDao().getLyrics(other.trackKey)!!.provider)
    }

    @Test
    fun blank_selection_is_rejected_and_stored_lyrics_stay_unchanged() = runBlocking {
        val track = seedTrack("k_blank", plain = "Keep me", chords = null)

        val failure = runCatching {
            TeachModeBindingHelper.bindManualContentToTrack(db, track, "  \n ", TargetContentType.LYRICS, "lyrics-hub.net")
        }

        assertTrue(failure.isFailure)
        assertEquals("Keep me", db.lyricsDao().getLyrics("k_blank")!!.plainLyrics)
        assertEquals("cache:initial", db.lyricsDao().getLyrics("k_blank")!!.provider)
    }

    @Test
    fun replacement_guard_compares_against_text_of_the_selected_type() = runBlocking {
        seedTrack("k_guard", plain = (1..20).joinToString("\n") { "Line $it of the song" }, chords = "Am\nla")
        val cached = db.lyricsDao().getLyrics("k_guard")
        val track = db.musicDao().getTrackByKey("k_guard")

        val storedLyrics = TeachModeBindingHelper.existingContent(TargetContentType.LYRICS, cached, track)
        assertTrue(
            "One tapped line must not silently replace a 20-line song",
            TeachModeBindingHelper.isSignificantShrink(storedLyrics, "Line 7 of the song")
        )

        val storedChords = TeachModeBindingHelper.existingContent(TargetContentType.CHORDS, cached, track)
        assertEquals("Am\nla", storedChords)
        assertFalse(TeachModeBindingHelper.isSignificantShrink(storedChords, "Em\nla la la\nG\nmore"))
    }
}
