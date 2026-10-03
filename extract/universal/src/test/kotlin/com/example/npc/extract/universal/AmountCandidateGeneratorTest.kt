package com.example.npc.extract.universal

import com.example.npc.core.model.finance.CurrencyCode
import com.example.npc.core.text.Lexer
import com.example.npc.core.text.TextNormalizer
import com.example.npc.core.text.TokenStream
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class AmountCandidateGeneratorTest {

    private val normalizer = TextNormalizer.create()
    private val lexer = Lexer.create()
    private val generator = AmountCandidateGenerator.create()

    private fun tokenize(text: String): TokenStream {
        val norm = normalizer.normalize(text)
        return lexer.tokenize(norm)
    }

    @Test
    @DisplayName("MAIB Incident Push: 'Restituire 245,90 MDL ... Sold: 12 345,67 MDL' generates 2 candidates")
    fun testMaibIncidentPush() {
        val text = "Restituire 245,90 MDL\nTEMU.COM\nCard *1234\nSold: 12 345,67 MDL"
        val stream = tokenize(text)

        val candidates = generator.generate(stream, sourcePackage = "md.maib.maibank")

        assertThat(candidates).hasSize(2)

        // 1. Transaction Amount: 245,90 MDL
        val c1 = candidates[0]
        assertThat(c1.id).isEqualTo(0)
        assertThat(c1.minorUnits).isEqualTo(24590L)
        assertThat(c1.currencyCode).isEqualTo(CurrencyCode.MDL)
        assertThat(c1.isPrefixCurrency).isFalse()
        assertThat(c1.distanceTokens).isEqualTo(1)
        assertThat(text.substring(c1.span.start, c1.span.end)).isEqualTo("245,90 MDL")

        // 2. Balance: 12 345,67 MDL
        val c2 = candidates[1]
        assertThat(c2.id).isEqualTo(1)
        assertThat(c2.minorUnits).isEqualTo(1234567L)
        assertThat(c2.currencyCode).isEqualTo(CurrencyCode.MDL)
        assertThat(c2.isPrefixCurrency).isFalse()
        assertThat(c2.distanceTokens).isEqualTo(1)
        assertThat(text.substring(c2.span.start, c2.span.end)).isEqualTo("12 345,67 MDL")
    }

    @Test
    @DisplayName("Currency-bound invariant (ADR-302): isolated numbers without currency are strictly ignored")
    fun testIsolatedNumbersIgnored() {
        val stream = tokenize("Zakaz 98412 summa 500 no currency here")
        val candidates = generator.generate(stream)

        assertThat(candidates).isEmpty()
    }

    @Test
    @DisplayName("Exclusion of OTP codes and verification codes")
    fun testOtpExclusion() {
        // Pure OTP notification
        val otpStream = tokenize("Vash odnorazovyy kod podtverzhdeniya: 493011. Nikomu ne soobshchayte.")
        val otpCandidates = generator.generate(otpStream)
        assertThat(otpCandidates).isEmpty()

        // OTP notification with amount
        val mixedStream = tokenize("Kod 7721 dlya podtverzhdeniya oplaty 450,00 MDL v magazin")
        val mixedCandidates = generator.generate(mixedStream)

        assertThat(mixedCandidates).hasSize(1)
        assertThat(mixedCandidates[0].minorUnits).isEqualTo(45000L)
        assertThat(mixedCandidates[0].currencyCode).isEqualTo(CurrencyCode.MDL)
    }

    @Test
    @DisplayName("Exclusion of card masks, PAN and phone numbers")
    fun testCardAndPhoneExclusion() {
        val stream = tokenize("Oplata po karte *1234 na summu 150.00 MDL. Tel podderzhki: +37377712345")
        val candidates = generator.generate(stream)

        assertThat(candidates).hasSize(1)
        assertThat(candidates[0].minorUnits).isEqualTo(15000L)
        assertThat(candidates[0].currencyCode).isEqualTo(CurrencyCode.MDL)
    }

    @Test
    @DisplayName("Prefix currencies: '$ 150.00' and '€: 49.99'")
    fun testPrefixCurrencies() {
        val stream = tokenize("$ 150.00 and €: 49.99")
        val candidates = generator.generate(stream)

        assertThat(candidates).hasSize(2)

        assertThat(candidates[0].minorUnits).isEqualTo(15000L)
        assertThat(candidates[0].currencyCode).isEqualTo(CurrencyCode.USD)
        assertThat(candidates[0].isPrefixCurrency).isTrue()
        assertThat(candidates[0].distanceTokens).isEqualTo(1)

        assertThat(candidates[1].minorUnits).isEqualTo(4999L)
        assertThat(candidates[1].currencyCode).isEqualTo(CurrencyCode.EUR)
        assertThat(candidates[1].isPrefixCurrency).isTrue()
        assertThat(candidates[1].distanceTokens).isEqualTo(2)
    }

    @Test
    @DisplayName("Suffix currency with punctuation at distance 2: '500,00 - MDL'")
    fun testSuffixCurrencyDistance2() {
        val stream = tokenize("Spisanie: 500,00 - MDL")
        val candidates = generator.generate(stream)

        assertThat(candidates).hasSize(1)
        assertThat(candidates[0].minorUnits).isEqualTo(50000L)
        assertThat(candidates[0].currencyCode).isEqualTo(CurrencyCode.MDL)
        assertThat(candidates[0].distanceTokens).isEqualTo(2)
    }

    @Test
    @DisplayName("Newline breaks currency proximity: number and currency on different lines are not bound")
    fun testNewlineBreaksCurrencyProximity() {
        val stream = tokenize("500\nMDL")
        val candidates = generator.generate(stream)

        assertThat(candidates).isEmpty()
    }

    @Test
    @DisplayName("Regional ambiguous currency resolution: 'руб' -> RUP for APB/Prisbank, RUB for Russian banks")
    fun testAmbiguousCurrencyResolution() {
        val text = "Perevod 500,00 руб"
        val stream = tokenize(text)

        // APB -> RUP
        val apbCandidates = generator.generate(stream, sourcePackage = "com.apb.mobile")
        assertThat(apbCandidates).hasSize(1)
        assertThat(apbCandidates[0].minorUnits).isEqualTo(50000L)
        assertThat(apbCandidates[0].currencyCode).isEqualTo(CurrencyCode.RUP)

        // Sberbank -> RUB
        val sberCandidates = generator.generate(stream, sourcePackage = "ru.sberbankmobile")
        assertThat(sberCandidates).hasSize(1)
        assertThat(sberCandidates[0].minorUnits).isEqualTo(50000L)
        assertThat(sberCandidates[0].currencyCode).isEqualTo(CurrencyCode.RUB)
    }

    @Test
    @DisplayName("Empty token stream returns empty list without errors")
    fun testEmptyStream() {
        val stream = tokenize("")
        val candidates = generator.generate(stream)
        assertThat(candidates).isEmpty()
    }
}
