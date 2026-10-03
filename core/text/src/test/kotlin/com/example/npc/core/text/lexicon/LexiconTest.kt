package com.example.npc.core.text.lexicon

import com.example.npc.core.text.model.KeywordKind
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class LexiconTest {

    private val lexicon = LexiconLoader.default()

    @Test
    @DisplayName("Lexicon version verification")
    fun testVersion() {
        assertThat(lexicon.version).isEqualTo(1)
    }

    @Test
    @DisplayName("Keyword classification across 3 languages (RU, RO, EN)")
    fun testMultilingualKeywords() {
        // DECLINED
        assertThat(lexicon.matchKeyword("otkaz")).isEqualTo(KeywordKind.DECLINED)
        assertThat(lexicon.matchKeyword("отказ")).isEqualTo(KeywordKind.DECLINED)
        assertThat(lexicon.matchKeyword("отклонен")).isEqualTo(KeywordKind.DECLINED)
        assertThat(lexicon.matchKeyword("respins")).isEqualTo(KeywordKind.DECLINED)
        assertThat(lexicon.matchKeyword("refuzat")).isEqualTo(KeywordKind.DECLINED)
        assertThat(lexicon.matchKeyword("declined")).isEqualTo(KeywordKind.DECLINED)
        assertThat(lexicon.matchKeyword("insufficient")).isEqualTo(KeywordKind.DECLINED)

        // REFUND
        assertThat(lexicon.matchKeyword("vozvrat")).isEqualTo(KeywordKind.REFUND)
        assertThat(lexicon.matchKeyword("возврат")).isEqualTo(KeywordKind.REFUND)
        assertThat(lexicon.matchKeyword("restituire")).isEqualTo(KeywordKind.REFUND)
        assertThat(lexicon.matchKeyword("rambursare")).isEqualTo(KeywordKind.REFUND)
        assertThat(lexicon.matchKeyword("refund")).isEqualTo(KeywordKind.REFUND)

        // CREDIT
        assertThat(lexicon.matchKeyword("popolnenie")).isEqualTo(KeywordKind.CREDIT)
        assertThat(lexicon.matchKeyword("пополнение")).isEqualTo(KeywordKind.CREDIT)
        assertThat(lexicon.matchKeyword("зачисление")).isEqualTo(KeywordKind.CREDIT)
        assertThat(lexicon.matchKeyword("alimentare")).isEqualTo(KeywordKind.CREDIT)
        assertThat(lexicon.matchKeyword("incasare")).isEqualTo(KeywordKind.CREDIT)
        assertThat(lexicon.matchKeyword("credit")).isEqualTo(KeywordKind.CREDIT)
        assertThat(lexicon.matchKeyword("salary")).isEqualTo(KeywordKind.CREDIT)

        // TRANSFER
        assertThat(lexicon.matchKeyword("perevod")).isEqualTo(KeywordKind.TRANSFER)
        assertThat(lexicon.matchKeyword("перевод")).isEqualTo(KeywordKind.TRANSFER)
        assertThat(lexicon.matchKeyword("transfer")).isEqualTo(KeywordKind.TRANSFER)
        assertThat(lexicon.matchKeyword("p2p")).isEqualTo(KeywordKind.TRANSFER)

        // DEBIT
        assertThat(lexicon.matchKeyword("pokupka")).isEqualTo(KeywordKind.DEBIT)
        assertThat(lexicon.matchKeyword("покупка")).isEqualTo(KeywordKind.DEBIT)
        assertThat(lexicon.matchKeyword("оплата")).isEqualTo(KeywordKind.DEBIT)
        assertThat(lexicon.matchKeyword("списание")).isEqualTo(KeywordKind.DEBIT)
        assertThat(lexicon.matchKeyword("plata")).isEqualTo(KeywordKind.DEBIT)
        assertThat(lexicon.matchKeyword("cumparaturi")).isEqualTo(KeywordKind.DEBIT)
        assertThat(lexicon.matchKeyword("purchase")).isEqualTo(KeywordKind.DEBIT)
        assertThat(lexicon.matchKeyword("payment")).isEqualTo(KeywordKind.DEBIT)

        // BALANCE
        assertThat(lexicon.matchKeyword("ostatok")).isEqualTo(KeywordKind.BALANCE)
        assertThat(lexicon.matchKeyword("остаток")).isEqualTo(KeywordKind.BALANCE)
        assertThat(lexicon.matchKeyword("баланс")).isEqualTo(KeywordKind.BALANCE)
        assertThat(lexicon.matchKeyword("sold")).isEqualTo(KeywordKind.BALANCE)
        assertThat(lexicon.matchKeyword("disponibil")).isEqualTo(KeywordKind.BALANCE)
        assertThat(lexicon.matchKeyword("balance")).isEqualTo(KeywordKind.BALANCE)

        // OTP
        assertThat(lexicon.matchKeyword("kod")).isEqualTo(KeywordKind.OTP)
        assertThat(lexicon.matchKeyword("код")).isEqualTo(KeywordKind.OTP)
        assertThat(lexicon.matchKeyword("пароль")).isEqualTo(KeywordKind.OTP)
        assertThat(lexicon.matchKeyword("cod")).isEqualTo(KeywordKind.OTP)
        assertThat(lexicon.matchKeyword("code")).isEqualTo(KeywordKind.OTP)
        assertThat(lexicon.matchKeyword("otp")).isEqualTo(KeywordKind.OTP)
        assertThat(lexicon.matchKeyword("parola")).isEqualTo(KeywordKind.OTP)

        // PROMO
        assertThat(lexicon.matchKeyword("skidka")).isEqualTo(KeywordKind.PROMO)
        assertThat(lexicon.matchKeyword("скидка")).isEqualTo(KeywordKind.PROMO)
        assertThat(lexicon.matchKeyword("акция")).isEqualTo(KeywordKind.PROMO)
        assertThat(lexicon.matchKeyword("reducere")).isEqualTo(KeywordKind.PROMO)
        assertThat(lexicon.matchKeyword("promo")).isEqualTo(KeywordKind.PROMO)
        assertThat(lexicon.matchKeyword("cashback")).isEqualTo(KeywordKind.CREDIT)

        // FEE
        assertThat(lexicon.matchKeyword("komissiya")).isEqualTo(KeywordKind.FEE)
        assertThat(lexicon.matchKeyword("комиссия")).isEqualTo(KeywordKind.FEE)
        assertThat(lexicon.matchKeyword("comision")).isEqualTo(KeywordKind.FEE)
        assertThat(lexicon.matchKeyword("fee")).isEqualTo(KeywordKind.FEE)
    }

    @Test
    @DisplayName("Currency recognition: Exact vs Ambiguous")
    fun testCurrencyMatching() {
        // Exact MDL
        assertThat(lexicon.matchCurrency("mdl")).isEqualTo(CurrencyMatchResult.Exact("MDL"))
        assertThat(lexicon.matchCurrency("lei")).isEqualTo(CurrencyMatchResult.Exact("MDL"))
        assertThat(lexicon.matchCurrency("лей")).isEqualTo(CurrencyMatchResult.Exact("MDL"))

        // Exact RUP
        assertThat(lexicon.matchCurrency("rup")).isEqualTo(CurrencyMatchResult.Exact("RUP"))

        // Exact USD / EUR / RUB
        assertThat(lexicon.matchCurrency("usd")).isEqualTo(CurrencyMatchResult.Exact("USD"))
        assertThat(lexicon.matchCurrency("$")).isEqualTo(CurrencyMatchResult.Exact("USD"))
        assertThat(lexicon.matchCurrency("eur")).isEqualTo(CurrencyMatchResult.Exact("EUR"))
        assertThat(lexicon.matchCurrency("€")).isEqualTo(CurrencyMatchResult.Exact("EUR"))
        assertThat(lexicon.matchCurrency("rub")).isEqualTo(CurrencyMatchResult.Exact("RUB"))

        // Ambiguous руб / р. / р
        val ambiguousRub = lexicon.matchCurrency("руб")
        assertThat(ambiguousRub).isInstanceOf(CurrencyMatchResult.Ambiguous::class.java)
        val candidateCodes = (ambiguousRub as CurrencyMatchResult.Ambiguous).candidateCodes
        assertThat(candidateCodes).containsExactlyInAnyOrder("RUP", "RUB")

        assertThat(lexicon.matchCurrency("р.")).isInstanceOf(CurrencyMatchResult.Ambiguous::class.java)
        assertThat(lexicon.matchCurrency("р")).isInstanceOf(CurrencyMatchResult.Ambiguous::class.java)
        assertThat(lexicon.matchCurrency("rublej")).isInstanceOf(CurrencyMatchResult.Ambiguous::class.java)

        // Non-currency words return null
        assertThat(lexicon.matchCurrency("hello")).isNull()
    }

    @Test
    @DisplayName("Keyword prefix matching for inflected word forms")
    fun testPrefixMatching() {
        // "restituire" starts with stem "restitu"
        assertThat(lexicon.matchKeyword("restituire")).isEqualTo(KeywordKind.REFUND)
        // "cumpărături" -> keyForm "cumparaturi", starts with stem "cumparatur"
        assertThat(lexicon.matchKeyword("cumparaturi")).isEqualTo(KeywordKind.DEBIT)
        // "пополнением" starts with "пополн"
        assertThat(lexicon.matchKeyword("пополнением")).isEqualTo(KeywordKind.CREDIT)

        // Words shorter than 3 characters return null
        assertThat(lexicon.matchKeyword("de")).isNull()
        assertThat(lexicon.matchKeyword("la")).isNull()
        assertThat(lexicon.matchKeyword("in")).isNull()
    }

    @Test
    @DisplayName("ADR-185 Priority resolution: DECLINED > REFUND > TRANSFER > CREDIT > DEBIT")
    fun testPriorityResolution() {
        assertThat(CompactTrieLexiconRepository.selectPriority(KeywordKind.DECLINED, KeywordKind.REFUND))
            .isEqualTo(KeywordKind.DECLINED)
        assertThat(CompactTrieLexiconRepository.selectPriority(KeywordKind.REFUND, KeywordKind.DEBIT))
            .isEqualTo(KeywordKind.REFUND)
        assertThat(CompactTrieLexiconRepository.selectPriority(KeywordKind.TRANSFER, KeywordKind.CREDIT))
            .isEqualTo(KeywordKind.TRANSFER)
        assertThat(CompactTrieLexiconRepository.selectPriority(KeywordKind.CREDIT, KeywordKind.DEBIT))
            .isEqualTo(KeywordKind.CREDIT)
    }

    @Test
    @DisplayName("Performance benchmark: dictionary initialization <= 5 ms")
    fun testInitializationPerformance() {
        // Measure cold load
        val startTime = System.nanoTime()
        val loaded = LexiconLoader.loadFromResource("/lexicon_v1.json")
        val elapsedNanos = System.nanoTime() - startTime
        val elapsedMillis = elapsedNanos / 1_000_000.0

        println("Lexicon load time: ${String.format("%.3f", elapsedMillis)} ms")
        assertThat(loaded.version).isEqualTo(1)
        assertThat(elapsedMillis).isLessThan(5.0)
    }
}
