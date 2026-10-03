package com.example.npc.core.text

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.Random

class LexerFuzzTest {

    private val normalizer = TextNormalizer.create()
    private val lexer = Lexer.create()

    private val fuzzVocabulary = listOf(
        "12", "345", ",67", ".89", " ", "\n", "\r\n",
        "MDL", "RUP", "USD", "EUR", "RUB", "руб", "р.", "$", "€",
        "*", "**", "****", "card", "...", "1234",
        ":", "-", "/", "2026", "09", "28", "14", "05", "32",
        "%", "+", "-", "±",
        "Restituire", "Plata", "Sold", "Отказ", "Пополнение",
        "💳", "💸", "🚀", "№", "ﬁ",
        "a", "B", "z", "!", "?", "@", "#", "<", ">", "(", ")",
        "\u0000", "\u200B", "\u00A0", "\u202F", "ă", "î", "ș", "ț", "ё"
    )

    @Test
    @DisplayName("Fuzz-test on 100 000 random strings with 0 uncaught exceptions")
    fun testFuzz100kStrings() {
        val random = Random(42)
        val iterations = 100_000
        var totalTokens = 0L

        val startTime = System.currentTimeMillis()

        for (it in 0 until iterations) {
            val length = random.nextInt(15) // 0 to 14 vocabulary chunks
            val sb = StringBuilder()
            repeat(length) {
                sb.append(fuzzVocabulary[random.nextInt(fuzzVocabulary.size)])
            }
            val raw = sb.toString()

            val normalized = normalizer.normalize(raw)
            val stream = lexer.tokenize(normalized)
            totalTokens += stream.size

            // Basic integrity checks on each token
            for (idx in 0 until stream.size) {
                val token = stream[idx]
                assertThat(token.span.start).isGreaterThanOrEqualTo(0)
                assertThat(token.span.end).isGreaterThanOrEqualTo(token.span.start)
                assertThat(token.span.end).isLessThanOrEqualTo(normalized.normalized.length)
            }
        }

        val elapsed = System.currentTimeMillis() - startTime
        println("Completed 100,000 fuzz iterations in ${elapsed} ms. Total tokens produced: $totalTokens")
        assertThat(totalTokens).isGreaterThan(0)
    }

    @Test
    @DisplayName("Verify linear complexity O(N)")
    fun testLinearComplexity() {
        val sample = "Restituire 245,90 MDL Card *1234 Sold: 12 345,67 MDL\n"
        val sizes = listOf(500, 1000, 2000, 4000)
        val texts = sizes.map { size ->
            val sb = StringBuilder()
            while (sb.length < size) {
                sb.append(sample)
            }
            normalizer.normalize(sb.substring(0, size))
        }

        // Comprehensive warmup across all sizes
        repeat(3000) {
            for (norm in texts) {
                lexer.tokenize(norm)
            }
        }

        val iters = 2000
        val times = ArrayList<Double>()

        for (norm in texts) {
            val t0 = System.nanoTime()
            repeat(iters) {
                lexer.tokenize(norm)
            }
            val elapsedNanos = System.nanoTime() - t0
            val avgNanos = elapsedNanos.toDouble() / iters
            times.add(avgNanos)
            println("Size ${norm.normalized.length}: ${String.format("%.2f", avgNanos / 1000.0)} µs")
        }

        // Verify that 8x increase in size (500 -> 4000) results in < 15x increase in time (strictly O(N), not O(N^2))
        val scalingFactor = times.last() / times.first()
        val sizeFactor = sizes.last().toDouble() / sizes.first().toDouble()
        println("Size factor: $sizeFactor, Time scaling factor: ${String.format("%.2f", scalingFactor)}")

        assertThat(scalingFactor)
            .`as`("Execution time scales linearly with text length (scalingFactor < 15x for 8x size increase)")
            .isLessThan(15.0)
    }
}
