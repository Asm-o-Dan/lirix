package com.lirix.app

import com.lirix.app.storage.*
import com.lirix.app.translations.*
import com.lirix.app.ui.components.trackInfoPublicUrl
import com.lirix.app.ui.components.trackInfoSources
import com.lirix.app.ui.i18n.InterfaceLanguage
import com.lirix.app.ui.i18n.LirixStrings
import org.junit.Before
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class TrackInfoPresentationTest {
    private var previousLanguage = InterfaceLanguage.EN

    @Before fun selectRussian() {
        previousLanguage = LirixStrings.currentLanguage
        LirixStrings.currentLanguage = InterfaceLanguage.RU
    }

    @After fun restoreLanguage() { LirixStrings.currentLanguage = previousLanguage }

    @Test fun manualSourceIsLocalizedInEnglish() {
        LirixStrings.currentLanguage = InterfaceLanguage.EN
        val cache = LyricsCacheEntity(trackKey = "track", plainLyrics = "Edited fixture", syncedLyricsLrc = null,
            chordsAmDm = null, userNotes = null, provider = "USER:EDIT")
        val row = trackInfoSources(cache, emptyList()).single()
        assertEquals("Lyrics", row.kind)
        assertEquals("Saved by you", row.source)
    }

    @Test fun hybridCacheKeepsLyricsAndChordAuthorsAndLinksSeparate() {
        val attribution = LyricsAttribution(
            lyrics = LyricsSourceAttribution("builtin:kugou", "https://lyrics.kugou.com/search?keyword=fixture"),
            chords = LyricsSourceAttribution("builtin:akkords-pro", "https://akkords.pro/songs/fixture/song/", "Fixture Arranger")
        )
        val cache = LyricsCacheEntity(trackKey = "track", plainLyrics = "Fixture words", chordsAmDm = "Am\nFixture words",
            syncedLyricsLrc = null, userNotes = null, provider = "builtin:kugou", sourceAttributionJson = attribution.encode())
        val rows = trackInfoSources(cache, emptyList())
        assertEquals(listOf("Текст", "Аккорды"), rows.map { it.kind })
        assertEquals(listOf("KuGou", "Akkords.pro"), rows.map { it.source })
        assertNull(rows[0].author)
        assertEquals("Fixture Arranger", rows[1].author)
        assertEquals(attribution.chords!!.sourceUrl, rows[1].url)
    }

    @Test fun translationAttributionUsesDocumentAuthorWithoutMixingOriginalSource() {
        val translation = SongTranslation("https://lyrsense.com/fixture/song", "Fixture", "Artist", "Перевод",
            "Fixture Translator", listOf(TranslationBlock("Fixture words", "Тестовый перевод")), sourceName = "Lyrsense")
        val row = SongTranslationEntity("track", "ru", translation.sourceUrl, TranslationDocuments.encode(translation), 1L)
        val info = trackInfoSources(null, listOf(row)).single()
        assertEquals("Перевод · Русский", info.kind)
        assertEquals("Lyrsense", info.source)
        assertEquals("Fixture Translator", info.author)
        assertEquals(translation.sourceUrl, info.url)
    }

    @Test fun legacyChordEnrichmentNeverInheritsTheTimedLyricsProvider() {
        val cache = LyricsCacheEntity(trackKey = "track", plainLyrics = "Fixture", syncedLyricsLrc = null,
            chordsAmDm = "Am\nFixture", userNotes = null, provider = "builtin:kugou",
            sourceAttributionJson = LyricsAttribution(lyrics = LyricsSourceAttribution("builtin:kugou",
                "https://lyrics.kugou.com/search?keyword=fixture")).encode())
        val info = trackInfoSources(cache, emptyList())
        assertEquals("KuGou", info[0].source)
        assertEquals("Источник не сохранён", info[1].source)
        assertNull(info[1].author)
        assertNull(info[1].url)
    }

    @Test fun legacyManualAndMalformedCacheDoNotInventAuthorsOrUrls() {
        val cache = LyricsCacheEntity(trackKey = "track", plainLyrics = "Edited fixture", syncedLyricsLrc = null,
            chordsAmDm = null, userNotes = null, provider = "USER:EDIT", sourceAttributionJson = "malformed")
        val row = trackInfoSources(cache, emptyList()).single()
        assertEquals("Сохранено вами", row.source)
        assertNull(row.author)
        assertNull(row.url)
        assertTrue(trackInfoSources(null, emptyList()).isEmpty())
    }

    @Test fun sourceLinksCannotLaunchScriptOrExposeAccesskeys() {
        listOf("javascript:alert(1)", "http://source.example/song", "https://user@source.example/song",
            "https://source.example/song?accesskey=private", "https://source.example/song?token=private").forEach {
            assertNull(trackInfoPublicUrl(it))
        }
        assertEquals("https://lyrsense.com/fixture/song", trackInfoPublicUrl("https://lyrsense.com/fixture/song"))
    }
}
