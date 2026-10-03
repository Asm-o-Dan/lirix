package com.example.npc.core.model

import com.example.npc.core.model.classify.Confidence
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class ConfidenceTest {

    @Test
    fun `Confidence allows values within 0_0 to 1_0 range`() {
        val zero = assertDoesNotThrow { Confidence(0.0) }
        assertEquals(0.0, zero.value)

        val half = assertDoesNotThrow { Confidence(0.5) }
        assertEquals(0.5, half.value)

        val max = assertDoesNotThrow { Confidence(1.0) }
        assertEquals(1.0, max.value)

        val high = assertDoesNotThrow { Confidence(0.85) }
        assertEquals(0.85, high.value)
    }

    @Test
    fun `Confidence throws IllegalArgumentException for values outside range`() {
        val belowZero = assertThrows<IllegalArgumentException> {
            Confidence(-0.01)
        }
        assertTrue(belowZero.message?.contains("range [0.0, 1.0]") == true)

        val aboveOne = assertThrows<IllegalArgumentException> {
            Confidence(1.01)
        }
        assertTrue(aboveOne.message?.contains("range [0.0, 1.0]") == true)

        assertThrows<IllegalArgumentException> { Confidence(-100.0) }
        assertThrows<IllegalArgumentException> { Confidence(5.0) }
    }

    @Test
    fun `companion constants are correctly initialized`() {
        assertEquals(Confidence(0.0), Confidence.ZERO)
        assertEquals(Confidence(1.0), Confidence.MAXIMUM)
        assertEquals(Confidence(0.85), Confidence.HIGH_THRESHOLD)
        assertEquals(Confidence(1.0), Confidence.PROTOTYPE_CONFIDENCE)
    }

    @Test
    fun `Comparable implementation orders confidence levels properly`() {
        assertTrue(Confidence.ZERO < Confidence.HIGH_THRESHOLD)
        assertTrue(Confidence.HIGH_THRESHOLD < Confidence.MAXIMUM)
        assertTrue(Confidence(0.5) <= Confidence(0.85))
        assertTrue(Confidence(0.9) >= Confidence(0.85))
        assertEquals(0, Confidence(1.0).compareTo(Confidence.MAXIMUM))
    }
}
