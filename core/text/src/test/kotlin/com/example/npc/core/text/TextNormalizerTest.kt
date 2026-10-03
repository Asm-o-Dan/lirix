package com.example.npc.core.text

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class TextNormalizerTest {

    private val normalizer: TextNormalizer = TextNormalizer.create()

    @Test
    @DisplayName("Empty string normalization")
    fun testEmptyString() {
        val result = normalizer.normalize("")
        assertThat(result.original).isEmpty()
        assertThat(result.normalized).isEmpty()
        assertThat(result.keyForm).isEmpty()
        assertThat(result.offsetMap.toOriginal(0)).isEqualTo(0)
        assertThat(result.offsetMap.toNormalized(0)).isEqualTo(0)
        assertThat(result.offsetMap.toOriginalSpan(0, 0)).isEqualTo(TextSpan(0, 0))
    }

    @Test
    @DisplayName("Unicode NFKC normalization")
    fun testNfkcNormalization() {
        // Fullwidth digits, ligatures, compatibility characters
        val input = "Сумма: １２３４ ﬁ № Ⅳ"
        val result = normalizer.normalize(input)
        assertThat(result.normalized).contains("1234")
        assertThat(result.normalized).contains("fi")
        assertThat(result.normalized).contains("No")
        assertThat(result.normalized).contains("IV")
    }

    @Test
    @DisplayName("Whitespace unification (\u00A0, \u202F, \u2009, \t, etc.) to standard space \u0020")
    fun testWhitespaceUnification() {
        val input = "Sold:\u00A012\u202F345,67\u2009MDL\tcont"
        val result = normalizer.normalize(input)
        assertThat(result.normalized).isEqualTo("Sold: 12 345,67 MDL cont")
    }

    @Test
    @DisplayName("Line break normalization (\r\n -> \n, isolated \r -> \n)")
    fun testLineBreakNormalization() {
        val input = "Line1\r\nLine2\rLine3\nLine4"
        val result = normalizer.normalize(input)
        assertThat(result.normalized).isEqualTo("Line1\nLine2\nLine3\nLine4")

        // Span mapping over \r\n
        // In "Line1\r\nLine2", Line1 is [0, 5), \r\n is [5, 7), Line2 is [7, 12)
        // Normalized "Line1\nLine2": Line1 is [0, 5), \n is [5, 6), Line2 is [6, 11)
        val simple = "Line1\r\nLine2"
        val normSimple = normalizer.normalize(simple)
        val newlineSpan = normSimple.offsetMap.toOriginalSpan(5, 6)
        assertThat(newlineSpan).isEqualTo(TextSpan(5, 7))
        assertThat(simple.substring(newlineSpan.start, newlineSpan.end)).isEqualTo("\r\n")

        val line2Span = normSimple.offsetMap.toOriginalSpan(6, 11)
        assertThat(line2Span).isEqualTo(TextSpan(7, 12))
        assertThat(simple.substring(line2Span.start, line2Span.end)).isEqualTo("Line2")
    }

    @Test
    @DisplayName("Removal of invisible and zero-width characters (\u200B, \uFEFF, \u00AD)")
    fun testInvisibleCharacterRemoval() {
        val input = "Ma\u200Bib\uFEFF Ba\u00ADnk"
        val result = normalizer.normalize(input)
        assertThat(result.normalized).isEqualTo("Maib Bank")

        // Offset check
        // "Ma\u200Bib": 'M'(0), 'a'(1), '\u200B'(2), 'i'(3), 'b'(4)
        // Norm "Maib": 'M'(0), 'a'(1), 'i'(2), 'b'(3)
        // Span of "ib" is [2, 4) in norm -> in original, 'i' is at 3, 'b' ends at 5 -> [3, 5)
        val spanIb = result.offsetMap.toOriginalSpan(2, 4)
        assertThat(spanIb).isEqualTo(TextSpan(3, 5))
        assertThat(input.substring(spanIb.start, spanIb.end)).isEqualTo("ib")
    }

    @Test
    @DisplayName("Romanian and Russian diacritics removal in keyForm")
    fun testDiacriticsRemovalInKeyForm() {
        val input = "Plată Cumpărături Încasare Șoim Țară Chișinău Пополнение счёта"
        val result = normalizer.normalize(input)

        // ă/â -> a, î -> i, ș/ş -> s, ț/ţ -> t, ё -> е
        assertThat(result.keyForm).isEqualTo(
            "plata cumparaturi incasare soim tara chisinau пополнение счета"
        )
        // Both normalized and keyForm should have identical length
        assertThat(result.keyForm.length).isEqualTo(result.normalized.length)
    }

    @Test
    @DisplayName("Cedilla variants in Romanian (ş -> s, ţ -> t)")
    fun testCedillaVariantsInKeyForm() {
        val input = "Atenţie la sfîrşit"
        val result = normalizer.normalize(input)
        assertThat(result.keyForm).isEqualTo("atentie la sfirsit")
    }

    @Test
    @DisplayName("Homoglyph substitution: Cyrillic М in Latin MDL")
    fun testHomoglyphMdl() {
        // Cyrillic \u041C in Latin MDL
        val input = "Restituire 245,90 \u041CDL"
        val result = normalizer.normalize(input)

        // In normalized, characters retain their original semantic Unicode representations
        assertThat(result.normalized).isEqualTo("Restituire 245,90 \u041CDL")
        // In keyForm, Cyrillic М in MDL is mapped to Latin 'm'
        assertThat(result.keyForm).isEqualTo("restituire 245,90 mdl")
    }

    @Test
    @DisplayName("Homoglyph substitution: Cyrillic м in Latin mdl (lowercase)")
    fun testHomoglyphMdlLowercase() {
        val input = "plata 100 \u043Cdl"
        val result = normalizer.normalize(input)
        assertThat(result.keyForm).isEqualTo("plata 100 mdl")
    }

    @Test
    @DisplayName("Emoji and surrogate pairs")
    fun testEmojiAndSurrogatePairs() {
        val input = "💳 Покупка: 245,90 MDL 🚀"
        val result = normalizer.normalize(input)

        assertThat(result.normalized).isEqualTo("💳 Покупка: 245,90 MDL 🚀")
        assertThat(result.keyForm).isEqualTo("💳 покупка: 245,90 mdl 🚀")

        // Emoji 💳 has length 2
        val emojiSpan = result.offsetMap.toOriginalSpan(0, 2)
        assertThat(emojiSpan).isEqualTo(TextSpan(0, 2))
        assertThat(input.substring(emojiSpan.start, emojiSpan.end)).isEqualTo("💳")

        val amountSpan = result.offsetMap.toOriginalSpan(12, 18)
        assertThat(amountSpan).isEqualTo(TextSpan(12, 18))
        assertThat(input.substring(amountSpan.start, amountSpan.end)).isEqualTo("245,90")
    }

    @Test
    @DisplayName("Real MAIB notification incident reproduction")
    fun testMaibIncidentPush() {
        val rawPush = "Restituire 245,90 \u041CDL\r\nTEMU.COM\r\nCard *1234\r\nSold:\u00A012\u202F345,67 MDL"
        val result = normalizer.normalize(rawPush)

        assertThat(result.normalized).isEqualTo("Restituire 245,90 \u041CDL\nTEMU.COM\nCard *1234\nSold: 12 345,67 MDL")
        assertThat(result.keyForm).isEqualTo("restituire 245,90 mdl\ntemu.com\ncard *1234\nsold: 12 345,67 mdl")

        // Verify span for "12 345,67" in normalized
        val target = "12 345,67"
        val startInNorm = result.normalized.indexOf(target)
        val endInNorm = startInNorm + target.length
        val origSpan = result.offsetMap.toOriginalSpan(startInNorm, endInNorm)

        assertThat(rawPush.substring(origSpan.start, origSpan.end)).isEqualTo("12\u202F345,67")
    }

    @Test
    @DisplayName("OffsetMap bounds validation and edge cases")
    fun testOffsetMapBounds() {
        val input = "Hello World"
        val result = normalizer.normalize(input)

        assertThat(result.offsetMap.toOriginal(-10)).isEqualTo(0)
        assertThat(result.offsetMap.toOriginal(100)).isEqualTo(11)
        assertThat(result.offsetMap.toNormalized(-5)).isEqualTo(0)
        assertThat(result.offsetMap.toNormalized(100)).isEqualTo(11)

        assertThrows<IllegalArgumentException> {
            result.offsetMap.toOriginalSpan(-1, 5)
        }
        assertThrows<IllegalArgumentException> {
            result.offsetMap.toOriginalSpan(5, 3)
        }
    }

    @Test
    @DisplayName("Performance benchmark: <= 0.2 ms on 500 characters")
    fun testPerformance500Chars() {
        val sample = "Restituire 245,90 \u041CDL\r\nTEMU.COM\r\nCard *1234\r\nSold:\u00A012\u202F345,67 MDL\r\n"
        val sb = StringBuilder()
        while (sb.length < 500) {
            sb.append(sample)
        }
        val text500 = sb.substring(0, 500)

        // Warmup JIT
        repeat(2000) {
            normalizer.normalize(text500)
        }

        // Measurement
        val iterations = 1000
        val startTime = System.nanoTime()
        repeat(iterations) {
            normalizer.normalize(text500)
        }
        val elapsedNanos = System.nanoTime() - startTime
        val avgMillisPerRun = (elapsedNanos / iterations.toDouble()) / 1_000_000.0

        println("Avg normalization time for 500 chars: ${String.format("%.4f", avgMillisPerRun)} ms")
        assertThat(avgMillisPerRun).isLessThan(0.20)
    }
}
