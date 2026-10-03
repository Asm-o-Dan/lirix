package com.example.npc.extract.universal

import com.example.npc.core.model.finance.CurrencyCode
import com.example.npc.extract.universal.profile.InMemorySourceProfileRegistry
import com.example.npc.extract.universal.profile.SourceProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceProfileRegistryTest {

    private val registry = InMemorySourceProfileRegistry()

    @Test
    fun `apb and prisbank resolve ambiguous rub to RUP`() {
        val apbProfile = registry.getProfile("com.apb.mobile")
        assertTrue(apbProfile.isKnownBankingApp)
        assertEquals(CurrencyCode.RUP, apbProfile.defaultCurrency)
        assertEquals(CurrencyCode.RUP, apbProfile.regionalAmbiguityResolver("100 руб"))

        val prisProfile = registry.getProfile("com.prisbank.app")
        assertTrue(prisProfile.isKnownBankingApp)
        assertEquals(CurrencyCode.RUP, prisProfile.defaultCurrency)
        assertEquals(CurrencyCode.RUP, prisProfile.regionalAmbiguityResolver("50 р."))
    }

    @Test
    fun `russian bank packages resolve ambiguous rub to RUB`() {
        val sberProfile = registry.getProfile("ru.sberbankmobile")
        assertTrue(sberProfile.isKnownBankingApp)
        assertEquals(CurrencyCode.RUB, sberProfile.defaultCurrency)
        assertEquals(CurrencyCode.RUB, sberProfile.regionalAmbiguityResolver("100 руб"))
    }

    @Test
    fun `maib resolves to MDL`() {
        val maibProfile = registry.getProfile("md.maib.maibank")
        assertTrue(maibProfile.isKnownBankingApp)
        assertEquals(CurrencyCode.MDL, maibProfile.defaultCurrency)
    }

    @Test
    fun `unknown app defaults to non-banking with RUB fallback`() {
        val unknown = registry.getProfile("com.random.messenger")
        assertFalse(unknown.isKnownBankingApp)
        assertEquals(CurrencyCode.RUB, unknown.regionalAmbiguityResolver("100 руб"))
    }
}
