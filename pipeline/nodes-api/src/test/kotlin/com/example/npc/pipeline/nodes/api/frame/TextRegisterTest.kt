package com.example.npc.pipeline.nodes.api.frame

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class TextRegisterTest {

    @Test
    fun `constructor validates capacity`() {
        assertThrows<IllegalArgumentException> {
            TextRegister(0)
        }
        assertThrows<IllegalArgumentException> {
            TextRegister(-1)
        }
        val reg = TextRegister(128)
        assertEquals(128, reg.capacity)
        assertEquals(0, reg.length)
        assertFalse(reg.isTruncated)
    }

    @Test
    fun `set short text within capacity`() {
        val reg = TextRegister(32)
        reg.set("Hello World")
        assertEquals(11, reg.length)
        assertFalse(reg.isTruncated)
        assertEquals('H', reg[0])
        assertEquals('d', reg[10])
        assertEquals("Hello World", reg.materializeString())
    }

    @Test
    fun `set text equal to capacity`() {
        val reg = TextRegister(5)
        reg.set("12345")
        assertEquals(5, reg.length)
        assertFalse(reg.isTruncated)
        assertEquals("12345", reg.materializeString())
    }

    @Test
    fun `set text exceeding capacity triggers truncation`() {
        val reg = TextRegister(5)
        reg.set("123456789")
        assertEquals(5, reg.length)
        assertTrue(reg.isTruncated)
        assertEquals("12345", reg.materializeString())
    }

    @Test
    fun `get throws on out of bounds index`() {
        val reg = TextRegister(10)
        reg.set("abc")
        assertThrows<IndexOutOfBoundsException> {
            reg[-1]
        }
        assertThrows<IndexOutOfBoundsException> {
            reg[3]
        }
    }

    @Test
    fun `subSequence unconditionally throws UnsupportedOperationException`() {
        val reg = TextRegister(10)
        reg.set("test")
        assertThrows<UnsupportedOperationException> {
            reg.subSequence(0, 2)
        }
    }

    @Test
    fun `clear resets length and truncation`() {
        val reg = TextRegister(5)
        reg.set("123456")
        assertTrue(reg.isTruncated)
        reg.clear()
        assertEquals(0, reg.length)
        assertFalse(reg.isTruncated)
        assertEquals("", reg.materializeString())
    }

    @Test
    fun `copyTo copies chars and updates truncation status`() {
        val src = TextRegister(10)
        src.set("12345678")

        val destSmall = TextRegister(4)
        src.copyTo(destSmall)
        assertEquals(4, destSmall.length)
        assertTrue(destSmall.isTruncated)
        assertEquals("1234", destSmall.materializeString())

        val destBig = TextRegister(20)
        src.copyTo(destBig)
        assertEquals(8, destBig.length)
        assertFalse(destBig.isTruncated)
        assertEquals("12345678", destBig.materializeString())
    }
}
