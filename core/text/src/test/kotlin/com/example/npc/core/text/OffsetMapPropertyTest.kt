package com.example.npc.core.text

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.Random

class OffsetMapPropertyTest {

    private val normalizer = TextNormalizer.create()

    private val alphabet = listOf(
        "a", "b", "c", "Z", "9", "0",
        "ă", "â", "î", "ș", "ț", "ş", "ţ",
        "Привет", "Счёт", "Перевод", "руб", "MDL", "\u041CDL",
        " ", "\u00A0", "\u202F", "\u2009", "\t", "   ",
        "\r\n", "\n", "\r",
        "\u200B", "\uFEFF", "\u00AD",
        "💳", "💸", "🏦", "🚀",
        "№", "ﬁ", "e\u0301", "a\u0306"
    )

    @Test
    @DisplayName("Property 1: Valid bounds and ordering for any arbitrary span")
    fun testValidBoundsAndOrdering() {
        val random = Random(42)
        repeat(500) {
            val text = generateRandomString(random, 15)
            val normalizedText = normalizer.normalize(text)
            val normLen = normalizedText.normalized.length
            val origLen = text.length

            repeat(20) {
                val start = if (normLen == 0) 0 else random.nextInt(normLen + 1)
                val end = if (normLen == 0) 0 else start + random.nextInt(normLen - start + 1)

                val span = normalizedText.offsetMap.toOriginalSpan(start, end)

                assertThat(span.start)
                    .`as`("Span start must be non-negative")
                    .isGreaterThanOrEqualTo(0)
                assertThat(span.end)
                    .`as`("Span end must be >= span start")
                    .isGreaterThanOrEqualTo(span.start)
                assertThat(span.end)
                    .`as`("Span end must not exceed original length")
                    .isLessThanOrEqualTo(origLen)

                if (start == end) {
                    assertThat(span.start)
                        .`as`("Empty normalized span must produce empty original span")
                        .isEqualTo(span.end)
                }
            }
        }
    }

    @Test
    @DisplayName("Property 2: Monotonicity of span projections")
    fun testMonotonicity() {
        val random = Random(1337)
        repeat(300) {
            val text = generateRandomString(random, 20)
            val normalizedText = normalizer.normalize(text)
            val normLen = normalizedText.normalized.length

            if (normLen > 1) {
                for (s1 in 0 until normLen) {
                    for (s2 in s1 until normLen) {
                        val span1 = normalizedText.offsetMap.toOriginalSpan(s1, normLen)
                        val span2 = normalizedText.offsetMap.toOriginalSpan(s2, normLen)
                        assertThat(span1.start)
                            .`as`("s1 <= s2 implies span1.start <= span2.start")
                            .isLessThanOrEqualTo(span2.start)
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("Property 3: Semantic projection — normalized tokens map to original sources")
    fun testSemanticProjection() {
        val random = Random(2026)
        repeat(500) {
            val text = generateRandomString(random, 12)
            val normalizedText = normalizer.normalize(text)
            val norm = normalizedText.normalized

            // Split normalized into words/tokens
            var idx = 0
            while (idx < norm.length) {
                // Find next non-whitespace segment
                while (idx < norm.length && norm[idx].isWhitespace()) idx++
                val wordStart = idx
                while (idx < norm.length && !norm[idx].isWhitespace()) idx++
                val wordEnd = idx

                if (wordStart < wordEnd) {
                    val wordInNorm = norm.substring(wordStart, wordEnd)
                    val origSpan = normalizedText.offsetMap.toOriginalSpan(wordStart, wordEnd)
                    val origSub = text.substring(origSpan.start, origSpan.end)

                    // Normalizing the original substring must contain or equal the normalized token
                    val reNormalizedSub = normalizer.normalize(origSub).normalized
                    assertThat(reNormalizedSub)
                        .`as`("Re-normalizing original span must contain normalized word '$wordInNorm'")
                        .contains(wordInNorm)
                }
            }
        }
    }

    @Test
    @DisplayName("Edge cases: Emoji, surrogate pairs, only invisible chars, only spaces")
    fun testEdgeCases() {
        // 1. Only invisible characters
        val invisibleOnly = "\u200B\uFEFF\u00AD\u200C\u200D"
        val normInvisible = normalizer.normalize(invisibleOnly)
        assertThat(normInvisible.normalized).isEmpty()
        assertThat(normInvisible.offsetMap.toOriginalSpan(0, 0)).isEqualTo(TextSpan(0, 0))

        // 2. Only spaces of different kinds
        val spacesOnly = "\u00A0 \u202F\t\u2009"
        val normSpaces = normalizer.normalize(spacesOnly)
        assertThat(normSpaces.normalized).isEqualTo("     ")
        val fullSpan = normSpaces.offsetMap.toOriginalSpan(0, 5)
        assertThat(fullSpan).isEqualTo(TextSpan(0, spacesOnly.length))

        // 3. Consecutive newlines with carriage returns
        val crlfs = "\r\n\r\n\r\n"
        val normCrlfs = normalizer.normalize(crlfs)
        assertThat(normCrlfs.normalized).isEqualTo("\n\n\n")
        val crlfSpan = normCrlfs.offsetMap.toOriginalSpan(0, 3)
        assertThat(crlfSpan).isEqualTo(TextSpan(0, crlfs.length))

        // 4. Surrogate pairs / Emojis
        val emojis = "💳💰🏦"
        val normEmojis = normalizer.normalize(emojis)
        assertThat(normEmojis.normalized).isEqualTo("💳💰🏦")
        // Each emoji is 2 chars in UTF-16
        val firstEmojiSpan = normEmojis.offsetMap.toOriginalSpan(0, 2)
        assertThat(firstEmojiSpan).isEqualTo(TextSpan(0, 2))
        assertThat(emojis.substring(firstEmojiSpan.start, firstEmojiSpan.end)).isEqualTo("💳")

        val secondEmojiSpan = normEmojis.offsetMap.toOriginalSpan(2, 4)
        assertThat(secondEmojiSpan).isEqualTo(TextSpan(2, 4))
        assertThat(emojis.substring(secondEmojiSpan.start, secondEmojiSpan.end)).isEqualTo("💰")

        val thirdEmojiSpan = normEmojis.offsetMap.toOriginalSpan(4, 6)
        assertThat(thirdEmojiSpan).isEqualTo(TextSpan(4, 6))
        assertThat(emojis.substring(thirdEmojiSpan.start, thirdEmojiSpan.end)).isEqualTo("🏦")
    }

    private fun generateRandomString(random: Random, length: Int): String {
        val sb = StringBuilder()
        repeat(length) {
            val token = alphabet[random.nextInt(alphabet.size)]
            sb.append(token)
        }
        return sb.toString()
    }
}
