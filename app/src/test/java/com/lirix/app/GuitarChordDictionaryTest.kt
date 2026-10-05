package com.lirix.app

import com.lirix.app.feature.lyrics.GuitarChordDictionary
import org.junit.Assert.*
import org.junit.Test

class GuitarChordDictionaryTest {

    @Test
    fun testCommonChordsExist() {
        val am = GuitarChordDictionary.getChord("Am")
        assertNotNull(am)
        assertEquals(listOf(-1, 0, 2, 2, 1, 0), am?.frets)
        assertEquals(1, am?.baseFret)

        val c = GuitarChordDictionary.getChord("C")
        assertNotNull(c)
        assertEquals(listOf(-1, 3, 2, 0, 1, 0), c?.frets)

        val em = GuitarChordDictionary.getChord("Em")
        assertNotNull(em)
        assertEquals(listOf(0, 2, 2, 0, 0, 0), em?.frets)
    }

    @Test
    fun testBarreChords() {
        val f = GuitarChordDictionary.getChord("F")
        assertNotNull(f)
        assertEquals(listOf(1, 3, 3, 2, 1, 1), f?.frets)
        assertEquals(1, f?.barre)

        val bm = GuitarChordDictionary.getChord("Bm")
        assertNotNull(bm)
        assertEquals(listOf(-1, 2, 4, 4, 3, 2), bm?.frets)
        assertEquals(2, bm?.barre)
    }

    @Test
    fun testRussianHAliases() {
        val hm = GuitarChordDictionary.getChord("Hm")
        assertNotNull(hm)
        assertEquals(listOf(-1, 2, 4, 4, 3, 2), hm?.frets)

        val h7 = GuitarChordDictionary.getChord("H7")
        assertNotNull(h7)
        assertEquals(listOf(-1, 2, 1, 2, 0, 2), h7?.frets)
    }

    @Test
    fun testSlashBassFallback() {
        val amG = GuitarChordDictionary.getChord("Am/G")
        assertNotNull(amG)
        assertEquals("Am", amG?.name)

        val cE = GuitarChordDictionary.getChord("C/E")
        assertNotNull(cE)
        assertEquals("C", cE?.name)
    }

    @Test
    fun testExtractChordsFromText() {
        val songText = """
            [Куплет 1]
            Am             Dm
            Ты помнишь, как все начиналось
            E7             Am
            Все было впервые и вновь
            F              C
            Как строили лодки, и лодки звались
            Dm             E7
            "Вера", "Надежда", "Любовь"
        """.trimIndent()

        val chords = GuitarChordDictionary.extractChordsFromText(songText)
        assertEquals(listOf("Am", "Dm", "E7", "F", "C"), chords)
    }
}
