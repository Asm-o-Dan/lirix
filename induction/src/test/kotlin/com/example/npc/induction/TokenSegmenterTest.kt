package com.example.npc.induction

import com.example.npc.core.text.DefaultTokenStream
import com.example.npc.core.text.Lexer
import com.example.npc.core.text.TextNormalizer
import com.example.npc.core.text.TextSpan
import com.example.npc.core.text.TokenStream
import com.example.npc.core.text.model.Token
import com.example.npc.core.text.model.TokenType
import com.example.npc.induction.model.LiteralSegment
import com.example.npc.induction.model.SlotAssignment
import com.example.npc.induction.model.SlotSegment
import com.example.npc.induction.model.SlotType
import com.example.npc.induction.model.VariableSegment
import com.example.npc.induction.model.VariableType
import com.example.npc.induction.model.WhitespaceSegment
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class TokenSegmenterTest {

    private val normalizer = TextNormalizer.create()
    private val lexer = Lexer.create()

    private fun tokenize(raw: String): Pair<String, TokenStream> {
        val normalized = normalizer.normalize(raw)
        val stream = lexer.tokenize(normalized)
        return normalized.normalized to stream
    }

    @Test
    @DisplayName("Overview Example 1: Restituire 245,90 MDL Card *1234")
    fun testOverviewExample1() {
        val raw = "Restituire 245,90 MDL Card *1234"
        val stream = DefaultTokenStream(
            listOf(
                Token(TokenType.WORD, "Restituire", TextSpan(0, 10)),
                Token(TokenType.NUMBER, "245,90", TextSpan(11, 17)),
                Token(TokenType.CURRENCY, "MDL", TextSpan(18, 21)),
                Token(TokenType.WORD, "Card", TextSpan(22, 26)),
                Token(TokenType.CARD_MASK, "*1234", TextSpan(27, 32))
            )
        )
        assertThat(stream.size).isEqualTo(5)

        val slots = listOf(
            SlotAssignment(SlotType.TX_AMOUNT, listOf(1, 2)),
            SlotAssignment(SlotType.CARD_MASK, listOf(4))
        )

        val result = TokenSegmenter.segment(stream, slots, raw)

        assertThat(result.totalLength).isEqualTo(raw.length)
        assertThat(result.segments).hasSize(4)

        // 1. LiteralSegment("Restituire ")
        val seg0 = result.segments[0]
        assertThat(seg0).isInstanceOf(LiteralSegment::class.java)
        assertThat(seg0.text).isEqualTo("Restituire ")
        assertThat(seg0.tokens).containsExactly(stream[0])

        // 2. SlotSegment(role=TX_AMOUNT, "245,90 MDL")
        val seg1 = result.segments[1] as SlotSegment
        assertThat(seg1.role).isEqualTo(SlotType.TX_AMOUNT)
        assertThat(seg1.text).isEqualTo("245,90 MDL")
        assertThat(seg1.tokens).containsExactly(stream[1], stream[2])

        // 3. LiteralSegment(" Card ")
        val seg2 = result.segments[2]
        assertThat(seg2).isInstanceOf(LiteralSegment::class.java)
        assertThat(seg2.text).isEqualTo(" Card ")
        assertThat(seg2.tokens).containsExactly(stream[3])

        // 4. SlotSegment(role=CARD_MASK, "*1234")
        val seg3 = result.segments[3] as SlotSegment
        assertThat(seg3.role).isEqualTo(SlotType.CARD_MASK)
        assertThat(seg3.text).isEqualTo("*1234")
        assertThat(seg3.tokens).containsExactly(stream[4])

        // Verify continuous string reconstruction
        val reconstructed = result.segments.joinToString("") { it.text }
        assertThat(reconstructed).isEqualTo(raw)

        // Verify token partition invariant: every token belongs to exactly one segment
        val allSegTokens = result.segments.flatMap { it.tokens }
        assertThat(allSegTokens).containsExactlyElementsOf(stream.toList())
    }

    @Test
    @DisplayName("Overview Example 2: Oplata 100 MDL 28.09.2026 14:00")
    fun testOverviewExample2() {
        val raw = "Oplata 100 MDL 28.09.2026 14:00"
        val (normText, stream) = tokenize(raw)

        // Tokens:
        // 0: "Oplata"
        // 1: "100"
        // 2: "MDL"
        // 3: "28.09.2026" (DATE)
        // 4: "14:00" (TIME)
        assertThat(stream.size).isEqualTo(5)

        val slots = listOf(
            SlotAssignment(SlotType.TX_AMOUNT, listOf(1, 2))
        )

        val result = TokenSegmenter.segment(stream, slots, normText)

        assertThat(result.totalLength).isEqualTo(normText.length)
        assertThat(result.segments).hasSize(6)

        // 1. LiteralSegment("Oplata ")
        assertThat(result.segments[0]).isInstanceOf(LiteralSegment::class.java)
        assertThat(result.segments[0].text).isEqualTo("Oplata ")
        assertThat(result.segments[0].tokens).containsExactly(stream[0])

        // 2. SlotSegment(TX_AMOUNT, "100 MDL")
        val seg1 = result.segments[1] as SlotSegment
        assertThat(seg1.role).isEqualTo(SlotType.TX_AMOUNT)
        assertThat(seg1.text).isEqualTo("100 MDL")
        assertThat(seg1.tokens).containsExactly(stream[1], stream[2])

        // 3. LiteralSegment(" ")
        assertThat(result.segments[2]).isInstanceOf(LiteralSegment::class.java)
        assertThat(result.segments[2].text).isEqualTo(" ")

        // 4. VariableSegment(DATE, "28.09.2026")
        val seg3 = result.segments[3] as VariableSegment
        assertThat(seg3.variableType).isEqualTo(VariableType.DATE)
        assertThat(seg3.text).isEqualTo("28.09.2026")
        assertThat(seg3.tokens).containsExactly(stream[3])

        // 5. LiteralSegment(" ")
        assertThat(result.segments[4]).isInstanceOf(LiteralSegment::class.java)
        assertThat(result.segments[4].text).isEqualTo(" ")

        // 6. VariableSegment(TIME, "14:00")
        val seg5 = result.segments[5] as VariableSegment
        assertThat(seg5.variableType).isEqualTo(VariableType.TIME)
        assertThat(seg5.text).isEqualTo("14:00")
        assertThat(seg5.tokens).containsExactly(stream[4])

        // Verify continuous string reconstruction
        val reconstructed = result.segments.joinToString("") { it.text }
        assertThat(reconstructed).isEqualTo(normText)

        // Verify token partition invariant
        val allSegTokens = result.segments.flatMap { it.tokens }
        assertThat(allSegTokens).containsExactlyElementsOf(stream.toList())
    }

    @Test
    @DisplayName("Overview Example 3: Sold: 10 MDL")
    fun testOverviewExample3() {
        val raw = "Sold: 10 MDL"
        val (normText, stream) = tokenize(raw)

        // Tokens:
        // 0: "Sold"
        // 1: ":"
        // 2: "10"
        // 3: "MDL"
        assertThat(stream.size).isEqualTo(4)

        val slots = listOf(
            SlotAssignment(SlotType.BALANCE, listOf(2, 3))
        )

        val result = TokenSegmenter.segment(stream, slots, normText)

        assertThat(result.totalLength).isEqualTo(normText.length)
        assertThat(result.segments).hasSize(2)

        // 1. LiteralSegment("Sold: ")
        assertThat(result.segments[0]).isInstanceOf(LiteralSegment::class.java)
        assertThat(result.segments[0].text).isEqualTo("Sold: ")
        assertThat(result.segments[0].tokens).containsExactly(stream[0], stream[1])

        // 2. SlotSegment(BALANCE, "10 MDL")
        val seg1 = result.segments[1] as SlotSegment
        assertThat(seg1.role).isEqualTo(SlotType.BALANCE)
        assertThat(seg1.text).isEqualTo("10 MDL")
        assertThat(seg1.tokens).containsExactly(stream[2], stream[3])

        // Check original token boundaries preserved
        assertThat(seg1.tokens[0].span.start).isEqualTo(6)
        assertThat(seg1.tokens[0].span.end).isEqualTo(8)
        assertThat(seg1.tokens[1].span.start).isEqualTo(9)
        assertThat(seg1.tokens[1].span.end).isEqualTo(12)

        assertThat(result.segments.joinToString("") { it.text }).isEqualTo(normText)
    }

    @Test
    @DisplayName("Segmenting text with newline produces WhitespaceSegment")
    fun testNewlineSegmentation() {
        val raw = "Plata 100 MDL\nTEMU.COM"
        val (normText, stream) = tokenize(raw)

        val nlTokenIndex = (0 until stream.size).first { stream[it].type == TokenType.NEWLINE }
        val slots = listOf(
            SlotAssignment(SlotType.AMOUNT, listOf(1, 2)),
            SlotAssignment(SlotType.MERCHANT, listOf(nlTokenIndex + 1))
        )

        val result = TokenSegmenter.segment(stream, slots, normText)

        val wsSegment = result.segments.firstOrNull { it is WhitespaceSegment }
        assertThat(wsSegment).isNotNull
        val ws = wsSegment as WhitespaceSegment
        assertThat(ws.hasNewline).isTrue
        assertThat(ws.text).isEqualTo("\n")

        val reconstructed = result.segments.joinToString("") { it.text }
        assertThat(reconstructed).isEqualTo(normText)
    }

    @Test
    @DisplayName("Empty token stream returns empty sequence or single whitespace")
    fun testEmptyTokenStream() {
        val (_, stream) = tokenize("")
        val result = TokenSegmenter.segment(stream, emptyList(), "")
        assertThat(result.segments).isEmpty()
        assertThat(result.totalLength).isEqualTo(0)
    }

    @Test
    @DisplayName("Message with no slots produces literals and variables")
    fun testNoSlots() {
        val raw = "Notice 28.09.2026"
        val (normText, stream) = tokenize(raw)

        val result = TokenSegmenter.segment(stream, emptyList(), normText)
        assertThat(result.segments).hasSize(2)
        assertThat(result.segments[0]).isInstanceOf(LiteralSegment::class.java)
        assertThat(result.segments[0].text).isEqualTo("Notice ")
        assertThat(result.segments[1]).isInstanceOf(VariableSegment::class.java)
        assertThat(result.segments[1].text).isEqualTo("28.09.2026")
    }

    @Test
    @DisplayName("Detection of overlapping slots throws IllegalArgumentException")
    fun testOverlappingSlotsThrows() {
        val raw = "Plata 100 MDL"
        val (normText, stream) = tokenize(raw)

        val overlappingSlots = listOf(
            SlotAssignment(SlotType.AMOUNT, listOf(1, 2)),
            SlotAssignment(SlotType.BALANCE, listOf(2)) // token 2 overlaps!
        )

        assertThrows<IllegalArgumentException> {
            TokenSegmenter.segment(stream, overlappingSlots, normText)
        }
    }

    @Test
    @DisplayName("Non-contiguous slot tokens throw IllegalArgumentException")
    fun testNonContiguousSlotTokensThrow() {
        val raw = "Plata 100 MDL Card *1234"
        val (normText, stream) = tokenize(raw)

        val invalidSlot = listOf(
            SlotAssignment(SlotType.AMOUNT, listOf(1, 3)) // tokens 1 and 3 are not contiguous
        )

        assertThrows<IllegalArgumentException> {
            TokenSegmenter.segment(stream, invalidSlot, normText)
        }
    }

    @Test
    @DisplayName("Out of bounds slot token index throws IllegalArgumentException")
    fun testOutOfBoundsSlotIndexThrow() {
        val raw = "Plata 100 MDL"
        val (normText, stream) = tokenize(raw)

        val invalidSlot = listOf(
            SlotAssignment(SlotType.AMOUNT, listOf(10)) // out of bounds
        )

        assertThrows<IllegalArgumentException> {
            TokenSegmenter.segment(stream, invalidSlot, normText)
        }
    }

    @Test
    @DisplayName("Segment without explicit originalText reconstructs correctly from tokens")
    fun testSegmentWithoutExplicitOriginalText() {
        val stream = DefaultTokenStream(
            listOf(
                Token(TokenType.WORD, "Restituire", TextSpan(0, 10)),
                Token(TokenType.NUMBER, "245,90", TextSpan(11, 17)),
                Token(TokenType.CURRENCY, "MDL", TextSpan(18, 21)),
                Token(TokenType.WORD, "Card", TextSpan(22, 26)),
                Token(TokenType.CARD_MASK, "*1234", TextSpan(27, 32))
            )
        )

        val slots = listOf(
            SlotAssignment(SlotType.TX_AMOUNT, listOf(1, 2)),
            SlotAssignment(SlotType.CARD_MASK, listOf(4))
        )

        val result = TokenSegmenter.segment(stream, slots)
        assertThat(result.segments).hasSize(4)
        assertThat(result.segments[0].text).isEqualTo("Restituire ")
        assertThat(result.segments[1].text).isEqualTo("245,90 MDL")
        assertThat(result.segments[2].text).isEqualTo(" Card ")
        assertThat(result.segments[3].text).isEqualTo("*1234")
        assertThat(result.totalLength).isEqualTo(32)
    }

    @Test
    @DisplayName("Lexer integration test with multi-line banking push")
    fun testWithRealLexerPush() {
        val raw = "Restituire 245,90 MDL\nTEMU.COM\ncard ...1234\nSold: 12 345,67 MDL"
        val (normText, stream) = tokenize(raw)

        // Find token indices
        val amountNum = (0 until stream.size).first { stream[it].text == "245,90" }
        val cardMask = (0 until stream.size).first { stream[it].type == TokenType.CARD_MASK }
        val balNum = (0 until stream.size).first { stream[it].text == "12 345,67" }

        val slots = listOf(
            SlotAssignment(SlotType.TX_AMOUNT, listOf(amountNum, amountNum + 1)),
            SlotAssignment(SlotType.CARD_MASK, listOf(cardMask)),
            SlotAssignment(SlotType.BALANCE, listOf(balNum, balNum + 1))
        )

        val result = TokenSegmenter.segment(stream, slots, normText)

        // Check all tokens partitioned
        val allTokens = result.segments.flatMap { it.tokens }
        assertThat(allTokens).containsExactlyElementsOf(stream.toList())

        // Check text continuity
        val reconstructed = result.segments.joinToString("") { it.text }
        assertThat(reconstructed).isEqualTo(normText)
    }
}
