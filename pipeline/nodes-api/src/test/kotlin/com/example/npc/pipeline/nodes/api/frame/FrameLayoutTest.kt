package com.example.npc.pipeline.nodes.api.frame

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class FrameLayoutTest {

    @Test
    fun `valid frame layout creation`() {
        val layout = FrameLayout(
            longSlots = 10,
            doubleSlots = 5,
            refSlots = 8,
            textSlots = 4,
            textCapacity = 2048,
            requiredInputMask = FrameLayout.MASK_INPUT_TITLE or FrameLayout.MASK_INPUT_TEXT
        )
        assertEquals(10, layout.longSlots)
        assertEquals(5, layout.doubleSlots)
        assertEquals(8, layout.refSlots)
        assertEquals(4, layout.textSlots)
        assertEquals(2048, layout.textCapacity)
        assertEquals(3L, layout.requiredInputMask)
    }

    @Test
    fun `constructor validates non-negative slots and positive capacity`() {
        assertThrows<IllegalArgumentException> {
            FrameLayout(-1, 0, 0, 0)
        }
        assertThrows<IllegalArgumentException> {
            FrameLayout(0, -1, 0, 0)
        }
        assertThrows<IllegalArgumentException> {
            FrameLayout(0, 0, -1, 0)
        }
        assertThrows<IllegalArgumentException> {
            FrameLayout(0, 0, 0, -1)
        }
        assertThrows<IllegalArgumentException> {
            FrameLayout(0, 0, 0, 0, textCapacity = 0)
        }
    }

    @Test
    fun `bitmask constants are distinct powers of two`() {
        assertEquals(1L shl 0, FrameLayout.MASK_INPUT_TITLE)
        assertEquals(1L shl 1, FrameLayout.MASK_INPUT_TEXT)
        assertEquals(1L shl 2, FrameLayout.MASK_INPUT_SENDER)
        assertEquals(1L shl 3, FrameLayout.MASK_INPUT_POST_TIME)
        assertEquals(1L shl 4, FrameLayout.MASK_INPUT_PACKAGE)
        assertEquals(1L shl 5, FrameLayout.MASK_INPUT_CHANNEL_ID)
    }
}
