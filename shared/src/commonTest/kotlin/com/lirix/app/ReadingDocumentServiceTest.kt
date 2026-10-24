package com.lirix.app

import com.lirix.app.feature.lyrics.romanization.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class ReadingDocumentServiceTest {
    private val backend = createPlatformReadingBackend()
    private fun read(text: String, lang: RomanizationLanguage = RomanizationLanguage.JAPANESE, tones: Boolean = true) = backend.read(text, lang, ReadingOptions(toneMarks = tones))

    @Test fun languageAmbiguityIsExplicitAndDocumentScoped() {
        assertEquals(RomanizationLanguage.AUTO, resolveDocumentLanguage(listOf("世界"), RomanizationLanguage.AUTO))
        assertEquals(RomanizationLanguage.JAPANESE, resolveDocumentLanguage(listOf("世界", "かな"), RomanizationLanguage.AUTO))
        assertEquals(RomanizationLanguage.NONE, resolveDocumentLanguage(listOf("Hello 🌍"), RomanizationLanguage.AUTO))
        assertEquals(RomanizationLanguage.JAPANESE, resolveDocumentLanguage(listOf("ｶﾅ"), RomanizationLanguage.AUTO))
        assertEquals(RomanizationLanguage.CHINESE, resolveDocumentLanguage(listOf("世界"), RomanizationLanguage.CHINESE))
    }
    @Test fun commonJapanesePhrasesHaveCompleteReading() {
        // Independent vocabulary as well as audited failures; full IPADIC dictionary, not screenshot replacement.
        for (text in listOf("失くす", "失くして", "失くした", "彷徨う", "彷徨って", "立ち止まる", "図書館", "電車", "泳いでいる", "歩いていた", "鳥が飛ぶ", "優しい人", "新しい靴", "静かな町")) {
            val result = read(text)
            assertEquals("$text -> $result", ReadingStatus.COMPLETE, result.status)
            assertEquals(text, result.originalText)
            assertTrue(result.unresolvedRanges.isEmpty())
            assertFalse(result.readingText.orEmpty().any { it in '\u3400'..'\u9FFF' })
        }
    }
    @Test fun contextualParticlesAndKanaSpelling() {
        assertEquals("kimi e", read("君へ").readingText)
        assertTrue(read("わたしは学生").readingText.orEmpty().contains(" wa "))
        assertTrue(read("はし").readingText.orEmpty().startsWith("ha"))
        assertEquals("matcha", read("まっちゃ").readingText)
        assertEquals("kan'i", read("かんい").readingText)
    }
    @Test fun targetNormalizationLeavesOriginalAndLatinUnchanged() {
        assertEquals(read("カタカナ").readingText, read("ｶﾀｶﾅ").readingText)
        assertEquals(read("が").readingText, read("か\u3099").readingText)
        val original = "Hello ＡＢＣ が 🌍"
        val result = read(original)
        assertEquals(original, result.originalText)
        assertTrue(result.readingText.orEmpty().startsWith("Hello ＡＢＣ "))
        assertTrue(result.readingText.orEmpty().endsWith(" 🌍"))
    }
    @Test fun chineseTraditionalAndPolyphonesAreContextual() {
        fun zh(text: String) = read(text, RomanizationLanguage.CHINESE)
        for ((input, expected) in mapOf("银行" to "yín háng", "行走" to "xíng zǒu", "重庆" to "chóng qìng", "重量" to "zhòng liàng", "少了" to "shǎo le", "少年" to "shào nián", "快乐" to "kuài lè", "音乐" to "yīn yuè")) {
            assertEquals(input, expected, zh(input).readingText)
            assertEquals(ReadingStatus.COMPLETE, zh(input).status)
        }
        assertEquals(zh("爱你").readingText, zh("愛你").readingText)
        assertEquals(zh("发光").readingText, zh("發光").readingText)
        assertEquals("yín háng", zh("銀行").readingText)
        assertEquals("yīn yuè", zh("音樂").readingText)
        assertEquals("nü", read("女", RomanizationLanguage.CHINESE, false).readingText)
    }
    @Test fun unresolvedSupplementaryHanIsExplicitAndRecoverable() {
        val original = "X𯿿Y"
        val result = read(original)
        assertEquals(ReadingStatus.PARTIAL, result.status)
        assertEquals(original, result.originalText)
        assertTrue(result.readingText.orEmpty().contains("𯿿"))
        assertTrue(result.unresolvedRanges.any { it.start <= 1 && it.endExclusive >= 3 })
        assertEquals(ReadingStatus.NEEDS_LANGUAGE, read("世界", RomanizationLanguage.AUTO).status)
    }
    @Test fun documentPreservesOccurrenceAndTimingAndLanguage() = runBlocking {
        val lines = listOf(ReadingSourceLine(0, 10, "世界"), ReadingSourceLine(1, 20, "かな"), ReadingSourceLine(2, 30, "世界"))
        val result = ReadingDocumentService().romanizeDocument(lines, RomanizationLanguage.AUTO)
        assertEquals(lines, result.map { it.source })
        assertEquals("sekai", result.first().result.readingText)
        assertEquals(result.first().result.readingText, result.last().result.readingText)
    }
    @Test fun cacheSeparatesLanguageOptionsAndVersion() = runBlocking {
        val calls = mutableListOf<String>()
        val fake = object : ReadingBackend {
            override var version = "v1"
            override fun read(text: String, language: RomanizationLanguage, options: ReadingOptions): ReadingResult {
                calls += "$language:${options.toneMarks}:$version"
                return ReadingResult(text, calls.last(), ReadingStatus.COMPLETE, language, backendId = version)
            }
        }
        val service = ReadingDocumentService(fake)
        val lines = listOf(ReadingSourceLine(0, 0, "世界"))
        service.romanizeDocument(lines, RomanizationLanguage.JAPANESE)
        service.romanizeDocument(lines, RomanizationLanguage.JAPANESE)
        service.romanizeDocument(lines, RomanizationLanguage.CHINESE)
        service.romanizeDocument(lines, RomanizationLanguage.CHINESE, ReadingOptions(false))
        fake.version = "v2"
        service.romanizeDocument(lines, RomanizationLanguage.CHINESE)
        assertEquals(4, calls.size)
    }
    @Test fun originalOnlyDoesNotInitializeBackend() = runBlocking {
        var calls = 0
        val fake = object : ReadingBackend {
            override val version = "test"
            override fun read(text: String, language: RomanizationLanguage, options: ReadingOptions): ReadingResult {
                calls++
                return ReadingResult(text, "ok", ReadingStatus.COMPLETE, language)
            }
        }
        ReadingDocumentService(fake).romanizeDocument(listOf(ReadingSourceLine(0, 1, "世界")), RomanizationLanguage.NONE)
        assertEquals(0, calls)
    }
    @Test fun startedCancelledOldSongCannotPublishAfterNewSong() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = java.util.concurrent.CountDownLatch(1)
        val backend = object : ReadingBackend {
            override val version = "blocking-fixture"
            override fun read(text: String, language: RomanizationLanguage, options: ReadingOptions): ReadingResult {
                if (text == "old") {
                    started.complete(Unit)
                    check(release.await(5, java.util.concurrent.TimeUnit.SECONDS))
                }
                return ReadingResult(text, "reading-$text", ReadingStatus.COMPLETE, language)
            }
        }
        val service = ReadingDocumentService(backend)
        var published = "none"
        val old = launch(Dispatchers.Default) {
            service.romanizeDocument(listOf(ReadingSourceLine(0, 0, "old")), RomanizationLanguage.JAPANESE)
            published = "old"
        }
        try {
            withTimeout(5_000) { started.await() } // Proves actual backend work started before cancellation.
            old.cancel()
            val new = async(Dispatchers.Default) {
                val result = service.romanizeDocument(listOf(ReadingSourceLine(0, 10, "new")), RomanizationLanguage.JAPANESE)
                published = result.single().source.text
                result
            }
            release.countDown()
            withTimeout(5_000) { old.join() }
            val result = withTimeout(5_000) { new.await() }
            assertTrue(old.isCancelled)
            assertEquals("new", published)
            assertEquals("reading-new", result.single().result.readingText)
        } finally { release.countDown(); old.cancelAndJoin() }
    }

    @Test fun missingBackendDataPreservesOriginalWithoutSuccess() = runBlocking {
        val absent = object : ReadingBackend {
            override val version = "absent-fixture"
            override fun read(text: String, language: RomanizationLanguage, options: ReadingOptions): ReadingResult =
                throw IllegalStateException("Dictionary absent")
        }
        val line = ReadingSourceLine(7, 12_345, "未知の名前")
        val actual = ReadingDocumentService(absent).romanizeDocument(listOf(line), RomanizationLanguage.JAPANESE).single()
        assertEquals(line, actual.source)
        assertEquals(line.text, actual.result.originalText)
        assertEquals(ReadingStatus.UNAVAILABLE, actual.result.status)
        assertNull(actual.result.readingText)
    }

    @Test fun unknownRangesMapToOriginalEvenAfterHalfwidthKanaNormalization() {
        val original = "ｶﾞ𯿿"
        for (language in listOf(RomanizationLanguage.JAPANESE, RomanizationLanguage.CHINESE)) {
            val result = read(original, language)
            assertEquals(ReadingStatus.PARTIAL, result.status)
            assertEquals(original, result.originalText)
            assertTrue(result.unresolvedRanges.any { it.start <= 2 && it.endExclusive >= 4 })
            assertTrue(result.unresolvedRanges.all { it.start >= 0 && it.endExclusive <= original.length })
        }
    }

}
