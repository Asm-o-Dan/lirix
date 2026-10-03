package com.example.npc.extract.finance

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AmountParserTest {

    @Test
    fun `parseToMinor parses standard decimal amounts with dot separator`() {
        assertEquals(15050L, AmountParser.parseMinor("150.50"))
        assertEquals(440L, AmountParser.parseMinor("4.40"))
        assertEquals(5000L, AmountParser.parseMinor("50.00"))
        assertEquals(123456L, AmountParser.parseMinor("1234.56"))
    }

    @Test
    fun `parseToMinor parses decimal amounts with comma separator`() {
        assertEquals(547L, AmountParser.parseMinor("5,47"))
        assertEquals(1515L, AmountParser.parseMinor("15,15"))
        assertEquals(2240L, AmountParser.parseMinor("22,40"))
        assertEquals(2575L, AmountParser.parseMinor("25,75"))
        assertEquals(42201L, AmountParser.parseMinor("422,01"))
    }

    @Test
    fun `parseToMinor parses numbers with thousands grouping spaces`() {
        assertEquals(125000L, AmountParser.parseMinor("1 250.00"))
        assertEquals(125050L, AmountParser.parseMinor("1 250,50"))
        assertEquals(10000000L, AmountParser.parseMinor("100 000.00"))
    }

    @Test
    fun `parseToMinor pads single decimal digit to 2 minor digits`() {
        assertEquals(440L, AmountParser.parseMinor("4.4"))
        assertEquals(1050L, AmountParser.parseMinor("10,5"))
    }

    @Test
    fun `parseToMinor multiplies whole numbers without decimal places by 100`() {
        assertEquals(5000L, AmountParser.parseMinor("50"))
        assertEquals(50000L, AmountParser.parseMinor("500"))
        assertEquals(700L, AmountParser.parseMinor("7"))
    }

    @Test
    fun `parseToMinor handles zero amounts`() {
        assertEquals(0L, AmountParser.parseMinor("0"))
        assertEquals(0L, AmountParser.parseMinor("0.00"))
        assertEquals(0L, AmountParser.parseMinor("0,00"))
    }

    @Test
    fun `parseToMinor returns null for invalid formats, negatives, or non-digits`() {
        assertNull(AmountParser.parseMinor(""))
        assertNull(AmountParser.parseMinor("   "))
        assertNull(AmountParser.parseMinor("abc"))
        assertNull(AmountParser.parseMinor("-50.00"))
        assertNull(AmountParser.parseMinor("10.20.30"))
        assertNull(AmountParser.parseMinor("a".repeat(33))) // length > 32
    }
}
