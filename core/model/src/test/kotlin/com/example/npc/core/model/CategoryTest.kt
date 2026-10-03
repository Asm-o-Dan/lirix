package com.example.npc.core.model

import com.example.npc.core.model.classify.Category
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CategoryTest {

    @Test
    fun `enum contains expected categories`() {
        val expected = setOf(
            "FINANCE",
            "COMMUNICATION",
            "MUSIC",
            "SERVICES",
            "ADVERTISEMENT",
            "OTHER",
            "UNCLASSIFIED"
        )
        val actual = Category.entries.map { it.name }.toSet()
        assertEquals(expected, actual)
    }

    @Test
    fun `UNKNOWN synonym returns UNCLASSIFIED`() {
        assertEquals(Category.UNCLASSIFIED, Category.UNKNOWN)
    }

    @Test
    fun `fromStringOrUnclassified parses valid categories case-insensitively`() {
        assertEquals(Category.FINANCE, Category.fromStringOrUnclassified("FINANCE"))
        assertEquals(Category.FINANCE, Category.fromStringOrUnclassified("finance"))
        assertEquals(Category.FINANCE, Category.fromStringOrUnclassified("  Finance  "))

        assertEquals(Category.COMMUNICATION, Category.fromStringOrUnclassified("communication"))
        assertEquals(Category.MUSIC, Category.fromStringOrUnclassified("MUSIC"))
        assertEquals(Category.SERVICES, Category.fromStringOrUnclassified("services"))
        assertEquals(Category.ADVERTISEMENT, Category.fromStringOrUnclassified("advertisement"))
        assertEquals(Category.OTHER, Category.fromStringOrUnclassified("other"))
        assertEquals(Category.UNCLASSIFIED, Category.fromStringOrUnclassified("unclassified"))
    }

    @Test
    fun `fromStringOrUnclassified returns UNCLASSIFIED for null blank and invalid strings`() {
        assertEquals(Category.UNCLASSIFIED, Category.fromStringOrUnclassified(null))
        assertEquals(Category.UNCLASSIFIED, Category.fromStringOrUnclassified(""))
        assertEquals(Category.UNCLASSIFIED, Category.fromStringOrUnclassified("   "))
        assertEquals(Category.UNCLASSIFIED, Category.fromStringOrUnclassified("garbage_text"))
    }

    @Test
    fun `fromStringOrUnknown delegates to fromStringOrUnclassified`() {
        assertEquals(Category.FINANCE, Category.fromStringOrUnknown("finance"))
        assertEquals(Category.UNCLASSIFIED, Category.fromStringOrUnknown(null))
        assertEquals(Category.UNCLASSIFIED, Category.fromStringOrUnknown("xyz"))
    }
}
