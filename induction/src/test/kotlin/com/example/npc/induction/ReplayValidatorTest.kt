package com.example.npc.induction

import com.example.npc.induction.model.AmountFormatSpec
import com.example.npc.induction.model.BuiltTemplate
import com.example.npc.induction.validator.HistoricalEventSample
import com.example.npc.induction.validator.ReplayValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplayValidatorTest {

    private val validTemplate = BuiltTemplate(
        pattern = """(?i)\Qrestituire\E\s+(?P<amount>\d+(?:,\d{2})?)\s+(?P<curr>mdl)""",
        namedGroups = listOf("amount", "curr"),
        constants = mapOf("opType" to "CREDIT"),
        amountFormat = AmountFormatSpec(',', ' '),
        requiredLiterals = listOf("restituire")
    )

    @Test
    fun `positive samples from same source are counted`() {
        val samples = listOf(
            HistoricalEventSample(
                eventId = 1L,
                sourcePackage = "md.maib.maibank",
                normalizedText = "restituire 245,90 mdl temu",
                isFinancial = true
            ),
            HistoricalEventSample(
                eventId = 2L,
                sourcePackage = "md.maib.maibank",
                normalizedText = "restituire 15,00 mdl store",
                isFinancial = true
            )
        )

        val report = ReplayValidator.validate(validTemplate, "md.maib.maibank", samples)
        assertEquals(2, report.positiveMatchesCount)
        assertEquals(0, report.negativeViolationsCount)
        assertTrue(report.canActivate)
    }

    @Test
    fun `negative match on otp or non-financial blocks activation`() {
        val samples = listOf(
            HistoricalEventSample(
                eventId = 10L,
                sourcePackage = "md.maib.maibank",
                normalizedText = "restituire 100 mdl kod dlya vhoda: 4492",
                isFinancial = false,
                isOtp = true
            )
        )

        val report = ReplayValidator.validate(validTemplate, "md.maib.maibank", samples)
        assertEquals(1, report.negativeViolationsCount)
        assertFalse(report.canActivate)
        assertTrue(report.diagnostics.isNotEmpty())
    }

    @Test
    fun `conflict is detected when existing transaction is present`() {
        val samples = listOf(
            HistoricalEventSample(
                eventId = 5L,
                sourcePackage = "md.maib.maibank",
                normalizedText = "restituire 200,00 mdl temu",
                isFinancial = true,
                existingTransaction = "ExistingTx#5"
            )
        )

        val report = ReplayValidator.validate(validTemplate, "md.maib.maibank", samples)
        assertEquals(1, report.conflictCount)
        assertEquals(listOf(5L), report.conflictingEventIds)
        assertEquals(0, report.negativeViolationsCount)
        assertTrue(report.canActivate)
    }
}
