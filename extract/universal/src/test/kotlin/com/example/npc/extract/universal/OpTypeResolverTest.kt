package com.example.npc.extract.universal

import com.example.npc.core.model.finance.TransactionType
import com.example.npc.core.text.Lexer
import com.example.npc.core.text.TextNormalizer
import com.example.npc.core.text.TokenStream
import com.example.npc.core.text.model.KeywordKind
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class OpTypeResolverTest {

    private val normalizer = TextNormalizer.create()
    private val lexer = Lexer.create()
    private val resolver = OpTypeResolver.create()

    private fun tokenize(text: String): TokenStream {
        val norm = normalizer.normalize(text)
        return lexer.tokenize(norm)
    }

    @Test
    @DisplayName("MAIB TEMU Incident: 'Restituire 245,90 MDL' resolves to CREDIT with isRefund = true")
    fun testMaibTemuIncidentPush() {
        val text = "Restituire 245,90 MDL\nTEMU.COM\nCard *1234\nSold: 12 345,67 MDL"
        val stream = tokenize(text)

        val resolution = resolver.resolve(stream)

        assertThat(resolution.transactionType).isEqualTo(TransactionType.CREDIT)
        assertThat(resolution.isRefund).isTrue()
        assertThat(resolution.isDeclined).isFalse()
        assertThat(resolution.dominantKeywordText).isEqualTo("Restituire")
        assertThat(resolution.dominantKeywordKind).isEqualTo(KeywordKind.REFUND)
        assertThat(resolution.confidence).isGreaterThanOrEqualTo(0.95f)
    }

    @Test
    @DisplayName("Hierarchy: DECLINED > REFUND ('Refuz restituire' resolves to DECLINED)")
    fun testHierarchyDeclinedOverRefund() {
        val text = "Refuz restituire 100 MDL: fonduri indisponibile"
        val stream = tokenize(text)

        val resolution = resolver.resolve(stream)

        assertThat(resolution.isDeclined).isTrue()
        assertThat(resolution.isRefund).isFalse()
        assertThat(resolution.transactionType).isEqualTo(TransactionType.UNKNOWN)
        assertThat(resolution.dominantKeywordKind).isEqualTo(KeywordKind.DECLINED)
        assertThat(resolution.dominantKeywordText).isEqualTo("Refuz")
    }

    @Test
    @DisplayName("Hierarchy: REFUND > DEBIT ('Restituire plata cu cardul' resolves to REFUND / CREDIT)")
    fun testHierarchyRefundOverDebit() {
        val text = "Restituire plata cu cardul 245,90 MDL"
        val stream = tokenize(text)

        val resolution = resolver.resolve(stream)

        assertThat(resolution.isRefund).isTrue()
        assertThat(resolution.isDeclined).isFalse()
        assertThat(resolution.transactionType).isEqualTo(TransactionType.CREDIT)
        assertThat(resolution.dominantKeywordKind).isEqualTo(KeywordKind.REFUND)
        assertThat(resolution.dominantKeywordText).isEqualTo("Restituire")
    }

    @Test
    @DisplayName("Hierarchy: DECLINED > DEBIT ('Refuz plata 50 MDL' resolves to DECLINED)")
    fun testHierarchyDeclinedOverDebit() {
        val text = "Refuz plata 50 MDL: fonduri insuficiente"
        val stream = tokenize(text)

        val resolution = resolver.resolve(stream)

        assertThat(resolution.isDeclined).isTrue()
        assertThat(resolution.isRefund).isFalse()
        assertThat(resolution.transactionType).isEqualTo(TransactionType.DEBIT)
        assertThat(resolution.dominantKeywordKind).isEqualTo(KeywordKind.DECLINED)
    }

    @Test
    @DisplayName("Hierarchy: TRANSFER > CREDIT ('Transfer pentru alimentare' resolves to TRANSFER)")
    fun testHierarchyTransferOverCredit() {
        val text = "Transfer pentru alimentare cont 1000 MDL"
        val stream = tokenize(text)

        val resolution = resolver.resolve(stream)

        assertThat(resolution.transactionType).isEqualTo(TransactionType.TRANSFER)
        assertThat(resolution.isRefund).isFalse()
        assertThat(resolution.isDeclined).isFalse()
        assertThat(resolution.dominantKeywordKind).isEqualTo(KeywordKind.TRANSFER)
    }

    @Test
    @DisplayName("Conflicting credit and debit terms require review")
    fun testHierarchyCreditOverDebit() {
        val text = "Alimentare cont prin plata 500 MDL"
        val stream = tokenize(text)

        val resolution = resolver.resolve(stream)

        assertThat(resolution.transactionType).isEqualTo(TransactionType.UNKNOWN)
        assertThat(resolution.isRefund).isFalse()
        assertThat(resolution.isDeclined).isFalse()
        assertThat(resolution.dominantKeywordKind).isNull()
    }

    @Test
    @DisplayName("Direct DEBIT operation: 'Achitare 100 MDL'")
    fun testDirectDebit() {
        val text = "Achitare 100 MDL Magazin"
        val stream = tokenize(text)

        val resolution = resolver.resolve(stream)

        assertThat(resolution.transactionType).isEqualTo(TransactionType.DEBIT)
        assertThat(resolution.isRefund).isFalse()
        assertThat(resolution.isDeclined).isFalse()
        assertThat(resolution.dominantKeywordKind).isEqualTo(KeywordKind.DEBIT)
    }

    @Test
    @DisplayName("Russian Stems: 'Возврат', 'Отказ', 'Пополнение', 'Перевод', 'Оплата'")
    fun testRussianStems() {
        // 1. Возврат
        val refundRes = resolver.resolve(tokenize("Возврат 300 руб"))
        assertThat(refundRes.transactionType).isEqualTo(TransactionType.CREDIT)
        assertThat(refundRes.isRefund).isTrue()

        // 2. Отказ
        val declineRes = resolver.resolve(tokenize("Отказ в покупке 50 руб"))
        assertThat(declineRes.isDeclined).isTrue()

        // 3. Пополнение
        val creditRes = resolver.resolve(tokenize("Пополнение карты 1500 руб"))
        assertThat(creditRes.transactionType).isEqualTo(TransactionType.CREDIT)
        assertThat(creditRes.isRefund).isFalse()

        // 4. Перевод
        val transferRes = resolver.resolve(tokenize("Перевод 200 руб"))
        assertThat(transferRes.transactionType).isEqualTo(TransactionType.TRANSFER)

        // 5. Оплата
        val debitRes = resolver.resolve(tokenize("Оплата услуг 75 руб"))
        assertThat(debitRes.transactionType).isEqualTo(TransactionType.DEBIT)
    }

    @Test
    @DisplayName("Fallback on explicit sign '+' and '-'")
    fun testExplicitSignFallback() {
        val plusRes = resolver.resolve(tokenize("+ 100 MDL"))
        assertThat(plusRes.transactionType).isEqualTo(TransactionType.CREDIT)
        assertThat(plusRes.confidence).isEqualTo(0.70f)

        val minusRes = resolver.resolve(tokenize("- 50 MDL"))
        assertThat(minusRes.transactionType).isEqualTo(TransactionType.DEBIT)
        assertThat(minusRes.confidence).isEqualTo(0.70f)
    }

    @Test
    @DisplayName("No direction evidence yields UNKNOWN with zero confidence")
    fun testDefaultFallback() {
        val res = resolver.resolve(tokenize("100 MDL Supermarket"))
        assertThat(res.transactionType).isEqualTo(TransactionType.UNKNOWN)
        assertThat(res.isRefund).isFalse()
        assertThat(res.isDeclined).isFalse()
        assertThat(res.dominantKeyword).isNull()
        assertThat(res.confidence).isEqualTo(0f)
    }
}
