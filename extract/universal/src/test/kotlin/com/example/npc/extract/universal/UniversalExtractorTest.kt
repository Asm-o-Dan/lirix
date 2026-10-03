package com.example.npc.extract.universal

import com.example.npc.core.model.finance.CurrencyCode
import com.example.npc.core.model.finance.TransactionType
import com.example.npc.core.text.Lexer
import com.example.npc.core.text.TextNormalizer
import com.example.npc.extract.universal.profile.InMemorySourceProfileRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UniversalExtractorTest {

    private val registry = InMemorySourceProfileRegistry()
    private val extractor = UniversalExtractor.create(registry)

    @Test
    fun `maib temu refund notification is correctly extracted as ACCEPT with refund flag`() {
        val rawPush = "Restituire 245,90 MDL TEMU.COM Card *1234 Sold: 12 345,67 MDL"
        val normalized = TextNormalizer.normalize(rawPush)
        val tokens = Lexer.tokenize(normalized)

        val profile = registry.getProfile("md.maib.maibank")
        val result = extractor.extract(normalized, tokens, profile)

        assertEquals(ExtractionVerdict.ACCEPT, result.verdict)
        assertTrue(result.isRefund)
        assertNotNull(result.transaction)

        val tx = result.transaction!!
        assertEquals(24590L, tx.amount.minor)
        assertEquals(CurrencyCode.MDL, tx.amount.currency)
        assertEquals(TransactionType.CREDIT, tx.type)
        assertEquals("*1234", tx.accountMask)
        assertEquals("TEMU.COM", tx.merchant)
        assertNotNull(tx.balance)
        assertEquals(1234567L, tx.balance!!.minor)
        assertEquals(CurrencyCode.MDL, tx.balance!!.currency)
    }

    @Test
    fun `apb payment notification is correctly extracted as DEBIT`() {
        val rawPush = "Oplata 150.00 RUP Apteka Card *5678 Ostatok: 500.00 RUP"
        val normalized = TextNormalizer.normalize(rawPush)
        val tokens = Lexer.tokenize(normalized)

        val profile = registry.getProfile("com.apb.mobile")
        val result = extractor.extract(normalized, tokens, profile)

        assertEquals(ExtractionVerdict.ACCEPT, result.verdict)
        assertNotNull(result.transaction)

        val tx = result.transaction!!
        assertEquals(15000L, tx.amount.minor)
        assertEquals(CurrencyCode.RUP, tx.amount.currency)
        assertEquals(TransactionType.DEBIT, tx.type)
        assertEquals("*5678", tx.accountMask)
        assertNotNull(tx.balance)
        assertEquals(50000L, tx.balance!!.minor)
    }
}
