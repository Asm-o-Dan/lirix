package com.lirix.app.desktop

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.lirix.app.storage.AppDatabase
import com.lirix.app.translations.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger

/** Real routing/coordinator behavior with deterministic fake network providers. */
class EnglishTranslationFallbackTest {
    private fun doc(source: String = "https://example.test/a", title: String = "Synthetic title") =
        SongTranslation(source, title, "Synthetic artist", "Translated title", "Synthetic author",
            listOf(TranslationBlock("", "Synthetic translation")), "en", "Synthetic provider", false)
    private fun candidate(url: String = "https://example.test/a") = TranslationCandidate(url, "Synthetic title", "Synthetic artist", "Synthetic author")
    private class MemoryDao : SongTranslationDao {
        val rows = MutableStateFlow<List<SongTranslationEntity>>(emptyList())
        override fun observe(trackKey: String, language: String) = rows
        override suspend fun get(trackKey: String, language: String) = rows.value.filter { it.trackKey == trackKey && it.language == language }
        override suspend fun save(entity: SongTranslationEntity) { rows.value = rows.value.filterNot { it.trackKey == entity.trackKey && it.language == entity.language && it.sourceUrl == entity.sourceUrl } + entity }
    }
    private fun coordinator(dao: SongTranslationDao, providers: SongTranslationProviders) =
        AutomaticSongTranslations(dao, providers::find, preferences = MemoryTranslationLanguagePreferences("en"), searchLanguage = providers::findLanguage)

    @Test fun primaryFoundAutoSavesWithoutCallingFallback() = runBlocking {
        val fallbackCalls = AtomicInteger()
        val expected = doc()
        val router = SongTranslationProviders(
            englishPrimary = { _, _, _ -> TranslationSearchResult.Found(expected) },
            englishFallback = { _, _, _ -> fallbackCalls.incrementAndGet(); error("new provider must not be called") })
        val dao = MemoryDao()
        val coordinator = coordinator(dao, router)
        coordinator.resolve("track", "Synthetic title", "Synthetic artist", language = "en")
        assertEquals(0, fallbackCalls.get())
        assertEquals(AutomaticTranslationState.Ready, coordinator.states.value[coordinator.stateKey("track", "en")])
        assertEquals(expected, TranslationDocuments.decode(dao.rows.value.single().documentJson))
        assertEquals("en", dao.rows.value.single().language)
    }

    @Test fun primaryCandidatesRemainManualAndNeverQueryFallback() = runBlocking {
        val calls = AtomicInteger()
        val values = listOf(candidate(), candidate("https://example.test/b"))
        val router = SongTranslationProviders(
            englishPrimary = { _, _, _ -> TranslationSearchResult.Candidates(values) },
            englishFallback = { _, _, _ -> calls.incrementAndGet(); TranslationSearchResult.Missing })
        val dao = MemoryDao(); val coordinator = coordinator(dao, router)
        coordinator.resolve("track", "Synthetic title", "Synthetic artist", language = "en")
        assertEquals(0, calls.get())
        assertEquals(AutomaticTranslationState.Candidates(values), coordinator.states.value[coordinator.stateKey("track", "en")])
        assertTrue(dao.rows.value.isEmpty())
    }

    @Test fun confirmedPrimaryMissUsesFallbackAndHonorsMissingAndErrors() = runBlocking {
        var fallbackResult: TranslationSearchResult = TranslationSearchResult.Found(doc())
        var failure = false
        val router = SongTranslationProviders(
            englishPrimary = { _, _, _ -> TranslationSearchResult.Missing },
            englishFallback = { _, _, _ -> if (failure) error("provider detail must not escape") else fallbackResult })
        assertEquals(TranslationSearchResult.Found(doc()), router.findLanguage("Synthetic title", "Synthetic artist", "en", false))
        val dao = MemoryDao(); val coordinator = coordinator(dao, router)
        coordinator.resolve("track", "Synthetic title", "Synthetic artist", language = "en")
        assertEquals(doc(), TranslationDocuments.decode(dao.rows.value.single().documentJson))
        fallbackResult = TranslationSearchResult.Candidates(listOf(candidate()))
        assertEquals(fallbackResult, router.findLanguage("Synthetic title", "Synthetic artist", "en", true))
        fallbackResult = TranslationSearchResult.Missing
        assertEquals(TranslationSearchResult.Missing, router.findLanguage("Synthetic title", "Synthetic artist", "en", false))
        failure = true
        val error = runCatching { router.findLanguage("Synthetic title", "Synthetic artist", "en", false) }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
        assertFalse(error!!.message.orEmpty().contains("provider detail"))
    }

    @Test fun primaryFailureShowsFallbackAsPartialWithoutAutosave() = runBlocking {
        val router = SongTranslationProviders(
            englishPrimary = { _, _, _ -> error("HTTP 403 private detail") },
            englishFallback = { _, _, _ -> TranslationSearchResult.Found(doc()) })
        val result = router.findLanguage("Synthetic title", "Synthetic artist", "en", false) as TranslationSearchResult.Candidates
        assertEquals(listOf("LyricsTranslate"), result.unavailableSources)
        assertEquals(listOf(candidate()), result.values)
        val dao = MemoryDao(); val coordinator = coordinator(dao, router)
        coordinator.resolve("track", "Synthetic title", "Synthetic artist", language = "en")
        assertEquals(AutomaticTranslationState.Candidates(listOf(candidate()), listOf("LyricsTranslate")), coordinator.states.value[coordinator.stateKey("track", "en")])
        assertTrue("partial results require user confirmation", dao.rows.value.isEmpty())
        val fallbackChoices = SongTranslationProviders(
            englishPrimary = { _, _, _ -> error("unavailable") },
            englishFallback = { _, _, _ -> TranslationSearchResult.Candidates(listOf(candidate(), candidate("https://example.test/b"))) })
        val choices = fallbackChoices.findLanguage("Synthetic title", "Synthetic artist", "en", false) as TranslationSearchResult.Candidates
        assertEquals(2, choices.values.size)
        assertEquals(listOf("LyricsTranslate"), choices.unavailableSources)
    }

    @Test fun incorrectPrimaryTargetIsNeverAutomaticallySaved() = runBlocking {
        val router = SongTranslationProviders(
            englishPrimary = { _, _, _ -> TranslationSearchResult.Found(doc().copy(language = "ru")) },
            englishFallback = { _, _, _ -> TranslationSearchResult.Found(doc("https://example.test/fallback")) })
        val dao = MemoryDao(); val coordinator = coordinator(dao, router)
        coordinator.resolve("track", "Synthetic title", "Synthetic artist", language = "en")
        assertTrue(dao.rows.value.isEmpty())
        val state = coordinator.states.value[coordinator.stateKey("track", "en")] as AutomaticTranslationState.Candidates
        assertEquals(listOf("LyricsTranslate"), state.unavailableSources)
        assertEquals("https://example.test/fallback", state.values.single().sourceUrl)
    }

    @Test fun failureAndMissingNeverBecomeNotFoundAndSourceNamesAreBounded() = runBlocking {
        val primaryFailure = SongTranslationProviders(
            englishPrimary = { _, _, _ -> error("unavailable") },
            englishFallback = { _, _, _ -> TranslationSearchResult.Missing })
        val dao = MemoryDao(); val failed = coordinator(dao, primaryFailure)
        failed.resolve("track", "Synthetic title", "Synthetic artist", language = "en")
        val state = failed.states.value[failed.stateKey("track", "en")]
        assertTrue(state is AutomaticTranslationState.Failed)
        assertFalse((state as AutomaticTranslationState.Failed).message.contains("unavailable"))
        val hostile = AutomaticSongTranslations(dao, { _, _, _ -> TranslationSearchResult.Missing },
            preferences = MemoryTranslationLanguagePreferences("en"), searchLanguage = { _, _, _, _ ->
                TranslationSearchResult.Candidates(listOf(candidate()), listOf("LyricsTranslate", "<untrusted>", "LyricsTranslate"))
            })
        hostile.resolve("another", "Synthetic title", "Synthetic artist", language = "en")
        assertEquals(listOf("LyricsTranslate"), (hostile.states.value[hostile.stateKey("another", "en")] as AutomaticTranslationState.Candidates).unavailableSources)
    }

    @Test fun providerTimeoutFallsBackButOuterTimeoutCancelsWithoutFallbackOrSave() = runBlocking {
        val calls = AtomicInteger()
        val router = SongTranslationProviders(
            englishPrimary = { _, _, _ -> awaitCancellation() },
            englishFallback = { _, _, _ -> calls.incrementAndGet(); TranslationSearchResult.Found(doc()) },
            englishSourceTimeoutMs = 60)
        val partial = router.findLanguage("Synthetic title", "Synthetic artist", "en", false) as TranslationSearchResult.Candidates
        assertEquals(listOf("LyricsTranslate"), partial.unavailableSources)
        assertEquals(1, calls.get())
        val nested = SongTranslationProviders(
            englishPrimary = { _, _, _ -> withTimeout(20) { awaitCancellation() } },
            englishFallback = { _, _, _ -> TranslationSearchResult.Found(doc()) },
            englishSourceTimeoutMs = 2_000)
        val nestedResult = nested.findLanguage("Synthetic title", "Synthetic artist", "en", false) as TranslationSearchResult.Candidates
        assertEquals(listOf("LyricsTranslate"), nestedResult.unavailableSources)
        val entered = CompletableDeferred<Unit>()
        val outerCalls = AtomicInteger()
        val outerRouter = SongTranslationProviders(
            englishPrimary = { _, _, _ -> entered.complete(Unit); awaitCancellation() },
            englishFallback = { _, _, _ -> outerCalls.incrementAndGet(); TranslationSearchResult.Found(doc()) },
            englishSourceTimeoutMs = 2_000)
        val dao = MemoryDao(); val coordinator = coordinator(dao, outerRouter)
        val worker = async { coordinator.resolve("old", "Synthetic title", "Synthetic artist", language = "en") }
        entered.await(); worker.cancelAndJoin()
        assertEquals(0, outerCalls.get())
        assertNull(coordinator.states.value[coordinator.stateKey("old", "en")])
        assertTrue(dao.rows.value.isEmpty())
        val outerTimeout = withTimeoutOrNull(50) { coordinator.resolve("old2", "Synthetic title", "Synthetic artist", language = "en"); "completed" }
        assertNull(outerTimeout)
        assertEquals(0, outerCalls.get())
        assertNull(coordinator.states.value[coordinator.stateKey("old2", "en")])
    }

    @Test fun manualSelectionWinsDuringFallbackAgainstRealRoomAndExistingCacheSkipsSearch() = runBlocking {
        val directory = Files.createTempDirectory("lirix-english-fallback-").toFile()
        val db = Room.databaseBuilder<AppDatabase>(name = directory.resolve("test.db").absolutePath).setDriver(BundledSQLiteDriver()).build()
        try {
            val dao = db.songTranslationDao()
            val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>(); val networkCalls = AtomicInteger()
            val router = SongTranslationProviders(
                englishPrimary = { _, _, _ -> networkCalls.incrementAndGet(); TranslationSearchResult.Missing },
                englishFallback = { _, _, _ -> entered.complete(Unit); release.await(); TranslationSearchResult.Found(doc("https://example.test/auto")) })
            val coordinator = coordinator(dao, router)
            val job = launch { coordinator.resolve("track", "Synthetic title", "Synthetic artist", language = "en") }
            entered.await()
            val manual = doc("https://example.test/manual").copy(translator = "User choice")
            val row = SongTranslationEntity("track", "en", manual.sourceUrl, TranslationDocuments.encode(manual), 7)
            dao.save(row); release.complete(Unit); job.join()
            assertEquals(listOf(row), dao.get("track", "en"))
            coordinator.resolve("track", "Synthetic title", "Synthetic artist", refresh = true, language = "en")
            assertEquals("cached manual selection must avoid another network search", 1, networkCalls.get())
        } finally { db.close(); directory.deleteRecursively() }
    }
}
