package com.lirix.app

import com.lirix.app.feature.lyrics.romanization.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Complete independent public-domain song fixtures, not entries from our lexical supplement. */
class RomanizationGoldenCorpusTest {
    private fun corpus(name: String): List<ReadingSourceLine> = requireNotNull(javaClass.getResourceAsStream("/fixtures/romanization/$name"))
        .bufferedReader(Charsets.UTF_8).use { it.readLines() }.mapIndexed { index, text -> ReadingSourceLine(index, index * 2000L, text) }
    private fun assertComplete(result: ReadingResult) {
        assertEquals(result.toString(), ReadingStatus.COMPLETE, result.status)
        assertTrue(result.unresolvedRanges.isEmpty())
        assertFalse(result.readingText.orEmpty().codePoints().anyMatch {
            Character.UnicodeScript.of(it) in setOf(Character.UnicodeScript.HAN, Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA) || it in 0x20000..0x323AF
        })
    }
    @Test fun completeJapaneseSongKeepsOccurrencesAndTimings() = runBlocking {
        val source = corpus("sakura-1888.txt")
        assertEquals(7, source.size)
        val results = ReadingDocumentService().romanizeDocument(source, RomanizationLanguage.AUTO)
        assertEquals(source, results.map { it.source })
        results.forEach { assertComplete(it.result); assertEquals(it.source.text, it.result.originalText) }
        assertEquals("sakurasakura。", results[0].result.readingText)
        assertEquals("yayoinosora wa。", results[1].result.readingText)
        assertTrue(results[3].result.readingText.orEmpty().contains("kumo"))
    }
    @Test fun completeMandarinSongPreservesRepeatedLinesAndNeedsExplicitLanguage() = runBlocking {
        val source = corpus("molihua-1763.txt")
        assertEquals(9, source.size)
        val service = ReadingDocumentService()
        val ambiguous = service.romanizeDocument(source, RomanizationLanguage.AUTO)
        assertTrue(ambiguous.all { it.result.status == ReadingStatus.NEEDS_LANGUAGE && it.result.readingText == null })
        val results = service.romanizeDocument(source, RomanizationLanguage.CHINESE)
        assertEquals(source, results.map { it.source })
        results.forEach { assertComplete(it.result); assertEquals(it.source.text, it.result.originalText) }
        assertEquals("hǎo yī duǒ mò lì huā，", results[0].result.readingText)
        assertEquals(results[0].result.readingText, results[1].result.readingText)
        assertNotEquals(results[0].source.occurrenceId, results[1].source.occurrenceId)
        assertEquals("cǎi yī duǒ dài，", results[4].result.readingText)
    }
}
