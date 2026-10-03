package com.example.npc.core.text

import com.example.npc.core.text.model.KeywordKind
import com.example.npc.core.text.model.TokenType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class LexerTest {

    private val normalizer = TextNormalizer.create()
    private val lexer = Lexer.create()

    private fun tokenize(raw: String): TokenStream {
        val normalized = normalizer.normalize(raw)
        return lexer.tokenize(normalized)
    }

    @Test
    @DisplayName("Empty string produces empty TokenStream")
    fun testEmpty() {
        val stream = tokenize("")
        assertThat(stream.size).isEqualTo(0)
        assertThat(stream.tokenAtOffset(0)).isNull()
        assertThat(stream.tokensInRange(0, 10)).isEmpty()
    }

    @Test
    @DisplayName("Number clusters: 12 345,67, 1.234,56, 245.9, 100")
    fun testNumberClusters() {
        val stream = tokenize("12 345,67 1.234,56 245.9 100")
        assertThat(stream.size).isEqualTo(4)

        // 1. "12 345,67"
        val t0 = stream[0]
        assertThat(t0.type).isEqualTo(TokenType.NUMBER)
        assertThat(t0.text).isEqualTo("12 345,67")
        assertThat(t0.interpretations).hasSize(1)
        assertThat(t0.interpretations[0].integerPart).isEqualTo(12345L)
        assertThat(t0.interpretations[0].fractionPart).isEqualTo(67)
        assertThat(t0.interpretations[0].decimalSeparator).isEqualTo(',')
        assertThat(t0.interpretations[0].groupingSeparator).isEqualTo(' ')

        // 2. "1.234,56"
        val t1 = stream[1]
        assertThat(t1.type).isEqualTo(TokenType.NUMBER)
        assertThat(t1.text).isEqualTo("1.234,56")
        assertThat(t1.interpretations).hasSize(1)
        assertThat(t1.interpretations[0].integerPart).isEqualTo(1234L)
        assertThat(t1.interpretations[0].fractionPart).isEqualTo(56)
        assertThat(t1.interpretations[0].decimalSeparator).isEqualTo(',')
        assertThat(t1.interpretations[0].groupingSeparator).isEqualTo('.')

        // 3. "245.9"
        val t2 = stream[2]
        assertThat(t2.type).isEqualTo(TokenType.NUMBER)
        assertThat(t2.text).isEqualTo("245.9")
        assertThat(t2.interpretations[0].integerPart).isEqualTo(245L)
        assertThat(t2.interpretations[0].fractionPart).isEqualTo(90) // minor units
        assertThat(t2.interpretations[0].decimalSeparator).isEqualTo('.')
        assertThat(t2.interpretations[0].groupingSeparator).isNull()

        // 4. "100"
        val t3 = stream[3]
        assertThat(t3.type).isEqualTo(TokenType.NUMBER)
        assertThat(t3.text).isEqualTo("100")
        assertThat(t3.interpretations[0].integerPart).isEqualTo(100L)
        assertThat(t3.interpretations[0].fractionPart).isNull()
    }

    @Test
    @DisplayName("Ambiguous number interpretations: 1,234 and 1.234")
    fun testAmbiguousNumberInterpretations() {
        val stream = tokenize("1,234 1.234")
        assertThat(stream.size).isEqualTo(2)

        val t0 = stream[0]
        assertThat(t0.interpretations).hasSize(2)
        // Grouping hypothesis (1234)
        assertThat(t0.interpretations[0].integerPart).isEqualTo(1234L)
        assertThat(t0.interpretations[0].fractionPart).isNull()
        // Decimal hypothesis (1.234)
        assertThat(t0.interpretations[1].integerPart).isEqualTo(1L)
        assertThat(t0.interpretations[1].fractionPart).isEqualTo(234)

        val t1 = stream[1]
        assertThat(t1.interpretations).hasSize(2)
        assertThat(t1.interpretations[0].integerPart).isEqualTo(1234L)
        assertThat(t1.interpretations[1].integerPart).isEqualTo(1L)
    }

    @Test
    @DisplayName("Currencies recognition: MDL, RUP, USD, EUR, RUB, лей, lei, руб, р., $, €")
    fun testCurrencies() {
        val stream = tokenize("MDL RUP USD EUR RUB лей lei руб р. $ €")

        val expected = listOf(
            "MDL" to false,
            "RUP" to false,
            "USD" to false,
            "EUR" to false,
            "RUB" to false,
            "лей" to false,
            "lei" to false,
            "руб" to true,
            "р." to true,
            "$" to false,
            "€" to false
        )

        assertThat(stream.size).isEqualTo(expected.size)
        for (idx in expected.indices) {
            val token = stream[idx]
            val (text, isAmbiguous) = expected[idx]
            assertThat(token.type).`as`("Token '$text' type").isEqualTo(TokenType.CURRENCY)
            assertThat(token.text).isEqualTo(text)
            assertThat(token.isCurrencyAmbiguous).`as`("Ambiguity of '$text'").isEqualTo(isAmbiguous)
        }
    }

    @Test
    @DisplayName("Currency homoglyph: Cyrillic М in MDL")
    fun testCurrencyHomoglyphMdl() {
        // Cyrillic \u041C in MDL
        val stream = tokenize("245,90 \u041CDL")
        assertThat(stream.size).isEqualTo(2)
        assertThat(stream[0].type).isEqualTo(TokenType.NUMBER)
        assertThat(stream[1].type).isEqualTo(TokenType.CURRENCY)
        assertThat(stream[1].text).isEqualTo("\u041CDL")
    }

    @Test
    @DisplayName("Card masks: *1234, **1234, **** 1234, card ...1234")
    fun testCardMasks() {
        val stream = tokenize("*1234 **1234 **** 1234 card ...1234")
        assertThat(stream.size).isEqualTo(4)

        assertThat(stream[0].type).isEqualTo(TokenType.CARD_MASK)
        assertThat(stream[0].text).isEqualTo("*1234")

        assertThat(stream[1].type).isEqualTo(TokenType.CARD_MASK)
        assertThat(stream[1].text).isEqualTo("**1234")

        assertThat(stream[2].type).isEqualTo(TokenType.CARD_MASK)
        assertThat(stream[2].text).isEqualTo("**** 1234")

        assertThat(stream[3].type).isEqualTo(TokenType.CARD_MASK)
        assertThat(stream[3].text).isEqualTo("card ...1234")
    }

    @Test
    @DisplayName("Date and Time recognition")
    fun testDateAndTime() {
        val stream = tokenize("28.09.2026 14:05:32 2026-09-28 09:30")
        assertThat(stream.size).isEqualTo(4)

        assertThat(stream[0].type).isEqualTo(TokenType.DATE)
        assertThat(stream[0].text).isEqualTo("28.09.2026")

        assertThat(stream[1].type).isEqualTo(TokenType.TIME)
        assertThat(stream[1].text).isEqualTo("14:05:32")

        assertThat(stream[2].type).isEqualTo(TokenType.DATE)
        assertThat(stream[2].text).isEqualTo("2026-09-28")

        assertThat(stream[3].type).isEqualTo(TokenType.TIME)
        assertThat(stream[3].text).isEqualTo("09:30")
    }

    @Test
    @DisplayName("URL, Signs, Percent, Newlines and Punctuation")
    fun testUrlsSignsAndPunct() {
        val stream = tokenize("+245,90% https://maib.md/info.\nDone!")
        val types = stream.map { it.type }

        assertThat(types).containsExactly(
            TokenType.SIGN,
            TokenType.NUMBER,
            TokenType.PERCENT,
            TokenType.URL,
            TokenType.PUNCT, // the trailing dot after URL
            TokenType.NEWLINE,
            TokenType.WORD,
            TokenType.PUNCT
        )
    }

    @Test
    @DisplayName("AUDIT-015: Domain names without prefix tokenized as indivisible URL tokens (TEMU.COM, aliexpress.com, maib.md)")
    fun testPrefixlessDomainTokenization() {
        val stream = tokenize("Возврат от TEMU.COM на карту. Оплата на aliexpress.com и maib.md. Заказ на booking.com:")
        val urlTokens = stream.filter { it.type == TokenType.URL }

        assertThat(urlTokens.map { it.text }).containsExactly(
            "TEMU.COM",
            "aliexpress.com",
            "maib.md",
            "booking.com"
        )

        // Trailing punctuation should be separate PUNCT tokens
        val temuIdx = stream.indexOfFirst { it.text == "TEMU.COM" }
        assertThat(temuIdx).isGreaterThanOrEqualTo(0)
        assertThat(stream[temuIdx].span).isEqualTo(TextSpan(11, 19))

        val maibIdx = stream.indexOfFirst { it.text == "maib.md" }
        assertThat(maibIdx).isGreaterThanOrEqualTo(0)
        assertThat(stream[maibIdx + 1].type).isEqualTo(TokenType.PUNCT)
        assertThat(stream[maibIdx + 1].text).isEqualTo(".")

        val bookingIdx = stream.indexOfFirst { it.text == "booking.com" }
        assertThat(bookingIdx).isGreaterThanOrEqualTo(0)
        assertThat(stream[bookingIdx + 1].type).isEqualTo(TokenType.PUNCT)
        assertThat(stream[bookingIdx + 1].text).isEqualTo(":")
    }

    @Test
    @DisplayName("AUDIT-015: Disambiguation between domains, numbers, dates and abbreviations")
    fun testDomainDisambiguation() {
        val stream = tokenize("150.00 MDL 1.234,56 28.09.2026 cont. 100. doc.pdf")
        val urlTokens = stream.filter { it.type == TokenType.URL }
        assertThat(urlTokens).isEmpty()

        val numberTokens = stream.filter { it.type == TokenType.NUMBER }
        assertThat(numberTokens.map { it.text }).contains("150.00", "1.234,56", "100")
    }

    @Test
    @DisplayName("Keywords classification from stems (DECLINED, REFUND, CREDIT, etc.)")
    fun testKeywords() {
        val stream = tokenize("Restituire Alimentare Отказ Пополнение Перевод Оплата Баланс Код Скидка")
        val kinds = stream.map { it.keywordKind }

        assertThat(kinds).containsExactly(
            KeywordKind.REFUND,
            KeywordKind.CREDIT,
            KeywordKind.DECLINED,
            KeywordKind.CREDIT,
            KeywordKind.TRANSFER,
            KeywordKind.DEBIT,
            KeywordKind.BALANCE,
            KeywordKind.OTP,
            KeywordKind.PROMO
        )
    }

    @Test
    @DisplayName("Real MAIB incident push tokenization and span projection")
    fun testMaibIncidentPushTokenization() {
        val rawPush = "Restituire 245,90 \u041CDL\r\nTEMU.COM\r\nCard *1234\r\nSold:\u00A012\u202F345,67 MDL"
        val norm = normalizer.normalize(rawPush)
        val stream = lexer.tokenize(norm)

        // Find amount token
        val amountToken = stream.first { it.type == TokenType.NUMBER && it.text == "245,90" }
        val origSpan = norm.offsetMap.toOriginalSpan(amountToken.span.start, amountToken.span.end)
        assertThat(rawPush.substring(origSpan.start, origSpan.end)).isEqualTo("245,90")

        // Find balance token
        val balanceToken = stream.first { it.type == TokenType.NUMBER && it.text == "12 345,67" }
        val balanceSpan = norm.offsetMap.toOriginalSpan(balanceToken.span.start, balanceToken.span.end)
        assertThat(rawPush.substring(balanceSpan.start, balanceSpan.end)).isEqualTo("12\u202F345,67")
    }

    @Test
    @DisplayName("TokenStream navigation: tokenAtOffset and tokensInRange")
    fun testTokenStreamNavigation() {
        val stream = tokenize("Plata 100 MDL")
        // "Plata" [0, 5), " " [5, 6), "100" [6, 9), " " [9, 10), "MDL" [10, 13)
        assertThat(stream.tokenAtOffset(0)).isEqualTo(0)
        assertThat(stream.tokenAtOffset(4)).isEqualTo(0)
        assertThat(stream.tokenAtOffset(5)).isNull() // whitespace
        assertThat(stream.tokenAtOffset(6)).isEqualTo(1)
        assertThat(stream.tokenAtOffset(11)).isEqualTo(2)

        val inRange = stream.tokensInRange(4, 11) // overlaps Plata, 100, and MDL
        assertThat(inRange).hasSize(3)
    }

    @Test
    @DisplayName("Length limit enforcement (> 4096 characters throws IllegalArgumentException)")
    fun testLengthLimit() {
        val longString = "A".repeat(4097)
        val norm = normalizer.normalize(longString)
        assertThrows<IllegalArgumentException> {
            lexer.tokenize(norm)
        }
    }
}
