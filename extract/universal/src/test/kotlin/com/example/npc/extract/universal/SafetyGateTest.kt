package com.example.npc.extract.universal

import com.example.npc.core.model.finance.CurrencyCode
import com.example.npc.core.model.finance.TransactionType
import com.example.npc.core.text.Lexer
import com.example.npc.core.text.TextNormalizer
import com.example.npc.core.text.model.KeywordKind
import com.example.npc.core.text.model.Token
import com.example.npc.core.text.model.TokenType
import com.example.npc.core.text.TextSpan
import com.example.npc.core.text.TokenStream
import com.example.npc.extract.universal.gate.SafetyGate
import com.example.npc.extract.universal.model.AmountCandidate
import com.example.npc.extract.universal.model.RoleAssignment
import com.example.npc.extract.universal.model.RoleAssignmentResult
import com.example.npc.extract.universal.model.SlotRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyGateTest {

    private val safetyGate = SafetyGate.Default

    @Test
    fun `maib temu refund push yields ACCEPT with high score`() {
        val rawText = "Restituire 245,90 MDL TEMU.COM Card *1234 Sold: 12 345,67 MDL"
        val normalized = TextNormalizer.normalize(rawText)
        val tokens = Lexer.tokenize(normalized)

        val txCandidate = AmountCandidate(
            id = 0,
            tokenIndex = 1,
            currencyTokenIndex = 2,
            minorUnits = 24590L,
            currencyCode = CurrencyCode.MDL,
            isPrefixCurrency = false,
            distanceTokens = 1,
            span = TextSpan(11, 21)
        )
        val balCandidate = AmountCandidate(
            id = 1,
            tokenIndex = 6,
            currencyTokenIndex = 7,
            minorUnits = 1234567L,
            currencyCode = CurrencyCode.MDL,
            isPrefixCurrency = false,
            distanceTokens = 1,
            span = TextSpan(38, 56)
        )

        val solution = RoleAssignmentResult(
            assignments = listOf(
                RoleAssignment(txCandidate, SlotRole.TX_AMOUNT, 0.95),
                RoleAssignment(balCandidate, SlotRole.BALANCE, 0.90)
            ),
            txAmount = txCandidate,
            balance = balCandidate,
            fee = null,
            totalScore = 1.85,
            confidence = 0.95f
        )

        val opType = OpTypeResolution(
            transactionType = TransactionType.CREDIT,
            isRefund = true,
            isDeclined = false,
            dominantKeyword = tokens.first { it.keywordKind == KeywordKind.REFUND },
            confidence = 0.96f
        )

        val result = safetyGate.evaluate(
            tokens = tokens,
            solution = solution,
            opType = opType,
            sourcePackage = "md.maib.maibank"
        )

        assertEquals(ExtractionVerdict.ACCEPT, result.verdict)
        assertFalse(result.isOtpVeto)
        assertFalse(result.isPromoVeto)
        assertTrue(result.finalScore >= 0.80f)
    }

    @Test
    fun `otp message triggers OTP Veto and REJECT`() {
        val rawText = "Vash kod 5849 dlya vhoda v bank. Nikomu ne soobshchayte"
        val normalized = TextNormalizer.normalize(rawText)
        val tokens = Lexer.tokenize(normalized)

        val fakeCandidate = AmountCandidate(
            id = 0,
            tokenIndex = 2,
            currencyTokenIndex = 3,
            minorUnits = 584900L,
            currencyCode = CurrencyCode.RUB,
            isPrefixCurrency = false,
            distanceTokens = 1,
            span = TextSpan(9, 13)
        )

        // Simulated solution that wrongly tried to parse something
        val solution = RoleAssignmentResult(
            assignments = listOf(RoleAssignment(fakeCandidate, SlotRole.TX_AMOUNT, 0.5)),
            txAmount = fakeCandidate,
            balance = null,
            fee = null,
            confidence = 0.50f
        )

        val opType = OpTypeResolution(
            transactionType = TransactionType.DEBIT,
            isRefund = false,
            isDeclined = false,
            dominantKeyword = null,
            confidence = 0.50f
        )

        val result = safetyGate.evaluate(
            tokens = tokens,
            solution = solution,
            opType = opType,
            sourcePackage = "com.bank.app"
        )

        assertEquals(ExtractionVerdict.REJECT, result.verdict)
        assertTrue(result.isOtpVeto)
        assertEquals(0.0f, result.finalScore, 0.001f)
    }

    @Test
    fun `promo push without card or balance triggers Promo Veto and REJECT`() {
        val rawText = "Skidki do 50% na vse tovary v magazine! Prihodite!"
        val normalized = TextNormalizer.normalize(rawText)
        val tokens = Lexer.tokenize(normalized)

        val fakeCandidate = AmountCandidate(
            id = 0,
            tokenIndex = 2,
            currencyTokenIndex = 3,
            minorUnits = 5000L,
            currencyCode = CurrencyCode.MDL,
            isPrefixCurrency = false,
            distanceTokens = 1,
            span = TextSpan(10, 13)
        )

        val solution = RoleAssignmentResult(
            assignments = listOf(RoleAssignment(fakeCandidate, SlotRole.TX_AMOUNT, 0.6)),
            txAmount = fakeCandidate,
            balance = null,
            fee = null,
            confidence = 0.60f
        )

        val opType = OpTypeResolution(
            transactionType = TransactionType.DEBIT,
            isRefund = false,
            isDeclined = false,
            dominantKeyword = null,
            confidence = 0.60f
        )

        val result = safetyGate.evaluate(
            tokens = tokens,
            solution = solution,
            opType = opType,
            sourcePackage = "com.store.app"
        )

        assertEquals(ExtractionVerdict.REJECT, result.verdict)
        assertTrue(result.isPromoVeto)
    }

    @Test
    fun `messenger message has discounted confidence`() {
        val rawText = "Plata 150 MDL v apteke"
        val normalized = TextNormalizer.normalize(rawText)
        val tokens = Lexer.tokenize(normalized)

        val candidate = AmountCandidate(
            id = 0,
            tokenIndex = 1,
            currencyTokenIndex = 2,
            minorUnits = 15000L,
            currencyCode = CurrencyCode.MDL,
            isPrefixCurrency = false,
            distanceTokens = 1,
            span = TextSpan(6, 13)
        )

        val solution = RoleAssignmentResult(
            assignments = listOf(RoleAssignment(candidate, SlotRole.TX_AMOUNT, 0.70)),
            txAmount = candidate,
            balance = null,
            fee = null,
            confidence = 0.70f
        )

        val opType = OpTypeResolution(
            transactionType = TransactionType.DEBIT,
            isRefund = false,
            isDeclined = false,
            dominantKeyword = null,
            confidence = 0.70f
        )

        val result = safetyGate.evaluate(
            tokens = tokens,
            solution = solution,
            opType = opType,
            sourcePackage = "org.telegram.messenger"
        )

        // In telegram, 0.6x discount is applied
        assertTrue(result.finalScore < 0.80f)
        assertFalse(result.isOtpVeto)
    }

    @Test
    fun `null solution returns REJECT`() {
        val tokens = com.example.npc.core.text.DefaultTokenStream(emptyList())
        val opType = OpTypeResolution(TransactionType.DEBIT, false, false, null, 0.5f)

        val result = safetyGate.evaluate(tokens, null, opType, "com.any.app")

        assertEquals(ExtractionVerdict.REJECT, result.verdict)
        assertEquals(0.0f, result.finalScore, 0.001f)
    }
}
