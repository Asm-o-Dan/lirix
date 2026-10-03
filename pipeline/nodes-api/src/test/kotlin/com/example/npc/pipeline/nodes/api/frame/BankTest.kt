package com.example.npc.pipeline.nodes.api.frame

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class BankTest {

    @Test
    fun `bank enum contains strictly 4 values in exact order`() {
        val entries = Bank.entries
        assertEquals(4, entries.size)
        assertEquals(Bank.LONG, entries[0])
        assertEquals(Bank.DOUBLE, entries[1])
        assertEquals(Bank.REF, entries[2])
        assertEquals(Bank.TEXT, entries[3])
    }

    @Test
    fun `bank valueOf parses valid strings and throws on invalid`() {
        assertEquals(Bank.LONG, Bank.valueOf("LONG"))
        assertEquals(Bank.DOUBLE, Bank.valueOf("DOUBLE"))
        assertEquals(Bank.REF, Bank.valueOf("REF"))
        assertEquals(Bank.TEXT, Bank.valueOf("TEXT"))

        assertThrows(IllegalArgumentException::class.java) {
            Bank.valueOf("INVALID")
        }
    }
}
