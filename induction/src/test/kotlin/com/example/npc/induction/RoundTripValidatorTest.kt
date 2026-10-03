package com.example.npc.induction

import com.example.npc.core.model.finance.CurrencyCode
import com.example.npc.induction.model.AmountFormatSpec
import com.example.npc.induction.model.BuiltTemplate
import com.example.npc.induction.validator.ExpectedSlot
import com.example.npc.induction.validator.RoundTripResult
import com.example.npc.induction.validator.RoundTripValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoundTripValidatorTest {

    @Test
    fun `valid maib template round-trip succeeds on original text`() {
        val pattern = """(?i)\Qrestituire\E\s+(?P<amount>\d+(?:,\d{2})?)\s+(?P<curr>mdl)\s+(?P<merchant>temu\.com)\s+\Qcard\E\s+(?P<card>\*\d{4})\s+\Qsold:\E\s+(?P<bal>\d+[\s\d]*(?:,\d{2})?)\s+(?P<balcurr>mdl)"""
        val template = BuiltTemplate(
            pattern = pattern,
            namedGroups = listOf("amount", "curr", "merchant", "card", "bal", "balcurr"),
            constants = emptyMap(),
            amountFormat = AmountFormatSpec(',', ' '),
            requiredLiterals = listOf("restituire", "card", "sold:"),
            defaultCurrency = CurrencyCode.MDL
        )

        val originalText = "restituire 245,90 mdl temu.com card *1234 sold: 12 345,67 mdl"
        val slots = listOf(
            ExpectedSlot("amount", "245,90"),
            ExpectedSlot("curr", "mdl"),
            ExpectedSlot("merchant", "temu.com"),
            ExpectedSlot("card", "*1234"),
            ExpectedSlot("bal", "12 345,67"),
            ExpectedSlot("balcurr", "mdl")
        )

        val result = RoundTripValidator.validate(
            template = template,
            originalNormalizedText = originalText,
            expectedSlots = slots,
            negativeCorpus = listOf(
                "vash kod avtorizatsii 4820",
                "oplata 100 mdl magazin"
            )
        )

        assertEquals(RoundTripResult.Success, result)
    }

    @Test
    fun `slot mismatch is detected when extracted value differs`() {
        val pattern = """(?i)\Qrestituire\E\s+(?P<amount>\d+)\s+(?P<curr>mdl)"""
        val template = BuiltTemplate(
            pattern = pattern,
            namedGroups = listOf("amount", "curr"),
            constants = emptyMap(),
            amountFormat = AmountFormatSpec(',', null),
            requiredLiterals = listOf("restituire"),
            defaultCurrency = CurrencyCode.MDL
        )

        val originalText = "restituire 245 mdl"
        val slots = listOf(
            ExpectedSlot("amount", "245,00"), // Mismatch!
            ExpectedSlot("curr", "mdl")
        )

        val result = RoundTripValidator.validate(
            template = template,
            originalNormalizedText = originalText,
            expectedSlots = slots
        )

        assertTrue(result is RoundTripResult.SlotMismatch)
        val mismatch = result as RoundTripResult.SlotMismatch
        assertEquals("amount", mismatch.slotName)
        assertEquals("245,00", mismatch.expectedValue)
        assertEquals("245", mismatch.actualValue)
    }

    @Test
    fun `no match on original is returned when pattern does not match`() {
        val pattern = """(?i)\Qoplata\E\s+(?P<amount>\d+)\s+(?P<curr>mdl)"""
        val template = BuiltTemplate(
            pattern = pattern,
            namedGroups = listOf("amount", "curr"),
            constants = emptyMap(),
            amountFormat = AmountFormatSpec('.', null),
            requiredLiterals = listOf("oplata"),
            defaultCurrency = CurrencyCode.MDL
        )

        val originalText = "restituire 245 mdl"
        val slots = listOf(ExpectedSlot("amount", "245"))

        val result = RoundTripValidator.validate(
            template = template,
            originalNormalizedText = originalText,
            expectedSlots = slots
        )

        assertTrue(result is RoundTripResult.NoMatchOnOriginal)
    }

    @Test
    fun `negative corpus violation is detected`() {
        // Overly broad pattern without strict literals
        val pattern = """(?i)(?P<amount>\d+)\s+(?P<curr>mdl)"""
        val template = BuiltTemplate(
            pattern = pattern,
            namedGroups = listOf("amount", "curr"),
            constants = emptyMap(),
            amountFormat = AmountFormatSpec('.', null),
            requiredLiterals = listOf("mdl"),
            defaultCurrency = CurrencyCode.MDL
        )

        val originalText = "100 mdl"
        val slots = listOf(ExpectedSlot("amount", "100"), ExpectedSlot("curr", "mdl"))

        val negativeCorpus = listOf("kod podtverzhdeniya: 100 mdl dlya vhoda")

        val result = RoundTripValidator.validate(
            template = template,
            originalNormalizedText = originalText,
            expectedSlots = slots,
            negativeCorpus = negativeCorpus
        )

        assertTrue(result is RoundTripResult.NegativeCorpusViolation)
    }
}
