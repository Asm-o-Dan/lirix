package com.example.npc.induction

import com.example.npc.core.model.finance.TransactionType
import com.example.npc.core.text.DefaultTokenStream
import com.example.npc.core.text.Lexer
import com.example.npc.core.text.TextNormalizer
import com.example.npc.core.text.TextSpan
import com.example.npc.core.text.model.Token
import com.example.npc.core.text.model.TokenType
import com.example.npc.induction.model.OpTypeResolution
import com.example.npc.induction.model.SlotAssignment
import com.example.npc.induction.model.SlotType
import com.example.npc.induction.validator.ExpectedSlot
import com.example.npc.induction.validator.RoundTripResult
import com.example.npc.induction.validator.RoundTripValidator
import com.google.re2j.Pattern
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class TemplateBuilderTest {

    private val normalizer = TextNormalizer.create()
    private val lexer = Lexer.create()

    @Test
    @DisplayName("MAIB TEMU Incident: build template with amount, curr, merchant, card/mask, bal, balcurr")
    fun testMaibTemuIncidentTemplateBuild() {
        val rawPush = "Restituire 245,90 MDL TEMU.COM Card *1234 Sold: 12 345,67 MDL"
        val norm = normalizer.normalize(rawPush)

        val stream = DefaultTokenStream(
            listOf(
                Token(TokenType.WORD, "Restituire", TextSpan(0, 10)),
                Token(TokenType.NUMBER, "245,90", TextSpan(11, 17)),
                Token(TokenType.CURRENCY, "MDL", TextSpan(18, 21)),
                Token(TokenType.WORD, "TEMU.COM", TextSpan(22, 30)),
                Token(TokenType.WORD, "Card", TextSpan(31, 35)),
                Token(TokenType.CARD_MASK, "*1234", TextSpan(36, 41)),
                Token(TokenType.WORD, "Sold", TextSpan(42, 46)),
                Token(TokenType.PUNCT, ":", TextSpan(46, 47)),
                Token(TokenType.NUMBER, "12 345,67", TextSpan(48, 57)),
                Token(TokenType.CURRENCY, "MDL", TextSpan(58, 61))
            )
        )

        val slots = listOf(
            SlotAssignment(SlotType.TX_AMOUNT, listOf(1, 2)),
            SlotAssignment(SlotType.MERCHANT, listOf(3)),
            SlotAssignment(SlotType.CARD_MASK, listOf(5)),
            SlotAssignment(SlotType.BALANCE, listOf(8, 9))
        )

        val segmented = TokenSegmenter.segment(stream, slots, rawPush)

        val opType = OpTypeResolution(
            transactionType = TransactionType.CREDIT,
            isRefund = true,
            isDeclined = false
        )

        val template = TemplateBuilder.build(segmented, opType, maskGroupName = "mask")

        // 1. Паттерн начинается с (?i)
        assertThat(template.pattern).startsWith("(?i)")

        // 2. Именованные группы: amount, curr, merchant, mask, bal, balcurr
        assertThat(template.namedGroups).contains("amount", "curr", "merchant", "mask", "bal", "balcurr")
        assertThat(template.pattern).contains("(?P<amount>")
        assertThat(template.pattern).contains("(?P<curr>")
        assertThat(template.pattern).doesNotContain("(?P<mask >")
        assertThat(template.pattern).contains("(?P<mask")
        assertThat(template.pattern).contains("(?P<merchant>")
        assertThat(template.pattern).contains("(?P<bal>")
        assertThat(template.pattern).contains("(?P<balcurr>")

        // 3. Экранирование литералов \Q...\E
        assertThat(template.pattern).contains("\\QRestituire\\E")
        assertThat(template.pattern).contains("\\QCard\\E")
        assertThat(template.pattern).contains("\\QSold\\E")

        // 4. Семантические константы
        assertThat(template.constants["transactionType"]).isEqualTo("CREDIT")
        assertThat(template.constants["isRefund"]).isEqualTo("true")
        assertThat(template.constants["isDeclined"]).isEqualTo("false")

        // 5. Определение формата суммы
        assertThat(template.amountFormat.decimalSeparator).isEqualTo(',')

        // 6. requiredLiterals для быстрого префильтра (>= 3 символа)
        assertThat(template.requiredLiterals).contains("restituire", "card", "sold")

        // 7. Компиляция в RE2/J и Round-Trip сопоставление на тексте
        val re2Pattern = Pattern.compile(template.pattern)
        val matcher = re2Pattern.matcher(rawPush)
        assertThat(matcher.find()).isTrue

        assertThat(matcher.group("amount")).isEqualTo("245,90")
        assertThat(matcher.group("curr")).isEqualTo("MDL")
        assertThat(matcher.group("merchant")).isEqualTo("TEMU.COM")
        assertThat(matcher.group("mask")).isEqualTo("1234")
        assertThat(matcher.group("bal")).isEqualTo("12 345,67")
        assertThat(matcher.group("balcurr")).isEqualTo("MDL")
    }

    @Test
    @DisplayName("Build template with (?P<card>...) when card group name is requested")
    fun testBuildWithCardGroupName() {
        val raw = "Plata 100 MDL *1234"
        val stream = DefaultTokenStream(
            listOf(
                Token(TokenType.WORD, "Plata", TextSpan(0, 5)),
                Token(TokenType.NUMBER, "100", TextSpan(6, 9)),
                Token(TokenType.CURRENCY, "MDL", TextSpan(10, 13)),
                Token(TokenType.CARD_MASK, "*1234", TextSpan(14, 19))
            )
        )

        val slots = listOf(
            SlotAssignment(SlotType.AMOUNT, listOf(1, 2)),
            SlotAssignment(SlotType.CARD_MASK, listOf(3))
        )

        val segmented = TokenSegmenter.segment(stream, slots, raw)
        val template = TemplateBuilder.build(segmented, maskGroupName = "card")

        assertThat(template.namedGroups).contains("amount", "curr", "card")
        assertThat(template.pattern).contains("(?P<card>")

        val pattern = Pattern.compile(template.pattern)
        val matcher = pattern.matcher(raw)
        assertThat(matcher.find()).isTrue
        assertThat(matcher.group("card")).isEqualTo("1234")
    }

    @Test
    @DisplayName("Variables (DATE and TIME) are generalized into non-capturing groups")
    fun testVariablesGeneralized() {
        val raw = "Oplata 100 MDL 28.09.2026 14:00"
        val norm = normalizer.normalize(raw)
        val stream = lexer.tokenize(norm)

        val slots = listOf(
            SlotAssignment(SlotType.TX_AMOUNT, listOf(1, 2))
        )

        val segmented = TokenSegmenter.segment(stream, slots, norm.normalized)
        val template = TemplateBuilder.build(segmented)

        assertThat(template.pattern).contains("(?i)")
        assertThat(template.pattern).contains("\\QOplata\\E")
        assertThat(template.pattern).contains("(?P<amount>")
        assertThat(template.pattern).contains("(?P<curr>")
        // DATE & TIME become non-capturing groups
        assertThat(template.pattern).contains("(?:" + SafeFragments.DATE + ")")
        assertThat(template.pattern).contains("(?:" + SafeFragments.TIME + ")")

        // Matches input string
        val pattern = Pattern.compile(template.pattern)
        val matcher = pattern.matcher(norm.normalized)
        assertThat(matcher.find()).isTrue
        assertThat(matcher.group("amount")).isEqualTo("100")
        assertThat(matcher.group("curr")).isEqualTo("MDL")
    }

    @Test
    @DisplayName("Right-Bounded Rule: Merchant at end of message appends line-end bound")
    fun testMerchantAtEndOfMessageRightBounded() {
        val raw = "Plata 100 MDL la McDonald's"
        val stream = DefaultTokenStream(
            listOf(
                Token(TokenType.WORD, "Plata", TextSpan(0, 5)),
                Token(TokenType.NUMBER, "100", TextSpan(6, 9)),
                Token(TokenType.CURRENCY, "MDL", TextSpan(10, 13)),
                Token(TokenType.WORD, "la", TextSpan(14, 16)),
                Token(TokenType.WORD, "McDonald's", TextSpan(17, 27))
            )
        )

        val slots = listOf(
            SlotAssignment(SlotType.TX_AMOUNT, listOf(1, 2)),
            SlotAssignment(SlotType.MERCHANT, listOf(4))
        )

        val segmented = TokenSegmenter.segment(stream, slots, raw)
        val template = TemplateBuilder.build(segmented)

        assertThat(template.namedGroups).contains("amount", "curr", "merchant")
        // Check that LINE_END (?:\n|$) is appended after merchant
        assertThat(template.pattern).contains("(?P<merchant>[^\\n]{2,64}?)(?:\\n|$)")

        val pattern = Pattern.compile(template.pattern)
        val matcher = pattern.matcher(raw)
        assertThat(matcher.find()).isTrue
        assertThat(matcher.group("merchant")).isEqualTo("McDonald's")
    }

    @Test
    @DisplayName("Build pattern directly using buildPattern helper")
    fun testBuildPatternHelper() {
        val raw = "Sold: 10 MDL"
        val norm = normalizer.normalize(raw)
        val stream = lexer.tokenize(norm)

        val slots = listOf(
            SlotAssignment(SlotType.BALANCE, listOf(2, 3))
        )

        val segmented = TokenSegmenter.segment(stream, slots, norm.normalized)
        val patternStr = TemplateBuilder.buildPattern(segmented)

        assertThat(patternStr).startsWith("(?i)")
        assertThat(patternStr).contains("(?P<bal>")
        assertThat(patternStr).contains("(?P<balcurr>")

        val pattern = Pattern.compile(patternStr)
        val matcher = pattern.matcher(raw)
        assertThat(matcher.find()).isTrue
        assertThat(matcher.group("bal")).isEqualTo("10")
        assertThat(matcher.group("balcurr")).isEqualTo("MDL")
    }

    @Test
    @DisplayName("OPT-PIPE-001: buildDecomposed generates compact anchor (< 60 chars) and slot extractors pipeline")
    fun testBuildDecomposedPipeline() {
        val raw = "Зачисление 150.50 MDL от Magazin карте *4321 Баланс: 1200.00"
        val norm = normalizer.normalize(raw)
        val stream = lexer.tokenize(norm)

        val slots = listOf(
            SlotAssignment(SlotType.AMOUNT, listOf(1, 2)),
            SlotAssignment(SlotType.MERCHANT, listOf(4)),
            SlotAssignment(SlotType.CARD_MASK, listOf(6)),
            SlotAssignment(SlotType.BALANCE, listOf(8))
        )

        val segmented = TokenSegmenter.segment(stream, slots, norm.normalized)
        val alternativeAnchors = listOf("Пополнение счета", "Перевод на карту", "Зачисление")

        val template = TemplateBuilder.buildDecomposed(
            sequence = segmented,
            alternativeAnchors = alternativeAnchors
        )

        // 1. Проверяем якорный паттерн: < 60 символов
        assertThat(template.pattern).isEqualTo("(?i)(?:Пополнение счета|Перевод на карту|Зачисление)")
        assertThat(template.pattern.length).isLessThan(60)

        // 2. Проверяем наличие спецификации декомпозиции
        assertThat(template.decomposedSpec).isNotNull
        val spec = template.decomposedSpec!!
        assertThat(spec.anchorPattern).isEqualTo(template.pattern)

        // 3. Проверяем слотовые правила (каждое 30-80 символов, заведомо < 256)
        assertThat(spec.slotRules).containsKey(SlotType.AMOUNT)
        assertThat(spec.slotRules).containsKey(SlotType.CARD_MASK)
        assertThat(spec.slotRules).containsKey(SlotType.MERCHANT)
        assertThat(spec.slotRules).containsKey(SlotType.BALANCE)

        for ((slot, rule) in spec.slotRules) {
            assertThat(rule.length).isLessThan(100)
            assertThat(rule.length).isGreaterThanOrEqualTo(30)
        }

        // 4. Валидация через TemplateLint проходит без ошибок
        val lintResult = TemplateLint.lint(template)
        assertThat(lintResult.isValid).isTrue
    }

    @Test
    @DisplayName("AUDIT-012: Relaxed template induction replaces unassigned card mask and balance for backfill")
    fun testAudit012RelaxedInductionMatchesHistoricalEventsWithDifferentCardsAndBalances() {
        val originalNotification = "Сумма 100 USD была зачислена на карту ***6159. Доступный остаток: 116.25 USD"
        val norm = normalizer.normalize(originalNotification)
        val stream = lexer.tokenize(norm)

        // Пользователь в One-Tap размечает только сумму операции
        val amountIndex = stream.toList().indexOfFirst { it.text == "100" }
        assertThat(amountIndex).isGreaterThanOrEqualTo(0)

        val slots = listOf(
            SlotAssignment(SlotType.TX_AMOUNT, listOf(amountIndex))
        )

        val segmented = TokenSegmenter.segment(stream, slots, norm.normalized)
        val template = TemplateBuilder.build(segmented)

        // 1. Проверяем, что маска карты и баланс заменены на гибкие группы
        assertThat(template.pattern).contains("(?<CARD_MASK>[*•\\d]{4,16})")
        assertThat(template.pattern).contains("(?<BALANCE>[\\d\\s.,]+)")
        assertThat(template.pattern).doesNotContain("\\Q***6159\\E")
        assertThat(template.pattern).doesNotContain("\\Q116.25\\E")

        // 2. Литералы не содержат динамических данных карты и остатка
        assertThat(template.requiredLiterals).doesNotContain("***6159", "116.25")

        // 3. Компиляция и сопоставление с оригиналом
        val re2 = Pattern.compile(template.pattern)
        val originalMatcher = re2.matcher(originalNotification)
        assertThat(originalMatcher.find()).isTrue
        assertThat(originalMatcher.group("amount")).isEqualTo("100")
        assertThat(originalMatcher.group("CARD_MASK")).isEqualTo("***6159")
        assertThat(originalMatcher.group("BALANCE")).isEqualTo("116.25")

        // 4. Успешный бэкфилл исторических событий того же банка с другими картами и балансами
        val history1 = "Сумма 250 USD была зачислена на карту ***4422. Доступный остаток: 980.50 USD"
        val m1 = re2.matcher(history1)
        assertThat(m1.find()).isTrue
        assertThat(m1.group("amount")).isEqualTo("250")
        assertThat(m1.group("CARD_MASK")).isEqualTo("***4422")
        assertThat(m1.group("BALANCE")).isEqualTo("980.50")

        val history2 = "Сумма 12.00 USD была зачислена на карту **6159. Доступный остаток: 128.25 USD"
        val m2 = re2.matcher(history2)
        assertThat(m2.find()).isTrue
        assertThat(m2.group("amount")).isEqualTo("12.00")
        assertThat(m2.group("CARD_MASK")).isEqualTo("**6159")
        assertThat(m2.group("BALANCE")).isEqualTo("128.25")

        // 5. Устойчивость к множественным пробелам между словами (\s+)
        val history3 = "Сумма  50  USD  была   зачислена на карту *9999.  Доступный остаток:  50 USD"
        val m3 = re2.matcher(history3)
        assertThat(m3.find()).isTrue
        assertThat(m3.group("amount")).isEqualTo("50")
        assertThat(m3.group("CARD_MASK")).isEqualTo("*9999")
        assertThat(m3.group("BALANCE")).isEqualTo("50")
    }

    @Test
    @DisplayName("AUDIT-013: Gap-Aware token emission preserves domain names without spaces and handles spaced punctuation")
    fun testAudit013GapAwareTokenEmission() {
        val samplePush = "Возврат суммы 17 MDL от TEMU.COM успешно обработан на карту ***6159 . Примененная комиссия : 0.01 USD . Доступный остаток : 16.25 USD ."
        val norm = normalizer.normalize(samplePush)
        val stream = lexer.tokenize(norm)

        // Размечаем только сумму операции 17
        val amountIdx = stream.toList().indexOfFirst { it.text == "17" }
        assertThat(amountIdx).isGreaterThanOrEqualTo(0)

        val slots = listOf(
            SlotAssignment(SlotType.TX_AMOUNT, listOf(amountIdx))
        )

        val segmented = TokenSegmenter.segment(stream, slots, norm.normalized)
        val template = TemplateBuilder.build(segmented)

        // 1. Проверяем, что TEMU.COM сформирован БЕЗ \s+ вокруг точки
        assertThat(template.pattern).doesNotContain("\\QTEMU\\E\\s+")
        assertThat(template.pattern).doesNotContain("\\s+\\QCOM\\E")
        assertThat(template.pattern.contains("\\QTEMU.COM\\E") || template.pattern.contains("\\QTEMU\\E\\Q.\\E\\QCOM\\E")).isTrue

        // 2. Проверяем, что перед знаками препинания со пробелом эмитится гибкий \s*
        assertThat(template.pattern).contains("\\s*\\Q:\\E")
        assertThat(template.pattern).contains("\\s*\\Q.\\E")

        // 3. Round-Trip валидация исходного пуша проходит успешно
        val expectedSlots = listOf(
            ExpectedSlot("amount", "17")
        )
        val roundTripResult = RoundTripValidator.validate(template, samplePush, expectedSlots)
        assertThat(roundTripResult).isEqualTo(RoundTripResult.Success)

        // 4. Проверяем сопоставление через RE2/J и извлечение данных
        val re2 = Pattern.compile(template.pattern)
        val matcher = re2.matcher(samplePush)
        assertThat(matcher.find()).isTrue
        assertThat(matcher.group("amount")).isEqualTo("17")
        assertThat(matcher.group("CARD_MASK")).isEqualTo("***6159")
        assertThat(matcher.group("BALANCE")).isEqualTo("16.25")

        // 5. Проверяем устойчивость к вариантам без пробелов перед двоеточием и точкой
        val variantNoSpacedPunct = "Возврат суммы 17 MDL от TEMU.COM успешно обработан на карту ***6159. Примененная комиссия: 0.01 USD. Доступный остаток: 16.25 USD."
        val mVariant = re2.matcher(variantNoSpacedPunct)
        assertThat(mVariant.find()).isTrue
        assertThat(mVariant.group("amount")).isEqualTo("17")
        assertThat(mVariant.group("CARD_MASK")).isEqualTo("***6159")
        assertThat(mVariant.group("BALANCE")).isEqualTo("16.25")
    }

    @Test
    @DisplayName("AUDIT-014: Multi-currency push with TX_AMOUNT (MDL), FEE, BALANCE and BALANCE_CURRENCY (USD)")
    fun testAudit014MultiCurrencyAndBalanceCurrency() {
        val samplePush = "Возврат суммы 17 MDL от TEMU.COM успешно обработан на карту ***6159. Примененная комиссия: 0.01 USD. Доступный остаток: 16.25 USD."
        val norm = normalizer.normalize(samplePush)
        val stream = lexer.tokenize(norm)
        val tokenList = stream.toList()

        val amountIdx = tokenList.indexOfFirst { it.text == "17" }
        val currIdx = tokenList.indexOfFirst { it.text == "MDL" }
        val cardIdx = tokenList.indexOfFirst { it.text.contains("6159") }
        val feeIdx = tokenList.indexOfFirst { it.text == "0.01" }
        val balIdx = tokenList.indexOfFirst { it.text == "16.25" }
        val balCurrIdx = tokenList.indices.last { tokenList[it].text == "USD" }

        val slots = listOf(
            SlotAssignment(SlotType.TX_AMOUNT, listOf(amountIdx)),
            SlotAssignment(SlotType.CURRENCY, listOf(currIdx)),
            SlotAssignment(SlotType.CARD_MASK, listOf(cardIdx)),
            SlotAssignment(SlotType.FEE, listOf(feeIdx)),
            SlotAssignment(SlotType.BALANCE, listOf(balIdx)),
            SlotAssignment(SlotType.BALANCE_CURRENCY, listOf(balCurrIdx))
        )

        val segmented = TokenSegmenter.segment(stream, slots, norm.normalized)
        val template = TemplateBuilder.build(segmented)
        val decomposed = TemplateBuilder.buildDecomposedSpec(segmented)
        val candidateTemplate = template.copy(decomposedSpec = decomposed)

        // 1. Проверяем именованные группы
        assertThat(template.namedGroups).contains("amount", "curr", "mask", "fee", "bal", "balcurr")
        assertThat(template.pattern).contains("(?P<amount>")
        assertThat(template.pattern).contains("(?P<curr>")
        assertThat(template.pattern).contains("(?P<fee>")
        assertThat(template.pattern).contains("(?P<bal>")
        assertThat(template.pattern).contains("(?P<balcurr>")

        // 2. Декомпозированная спецификация содержит отдельное правило для BALANCE_CURRENCY
        assertThat(decomposed.slotRules).containsKey(SlotType.BALANCE_CURRENCY)
        val balCurrRule = decomposed.slotRules[SlotType.BALANCE_CURRENCY]!!
        assertThat(balCurrRule).contains("(?P<balcurr>")

        // 3. Линтер одобряет спецификацию
        val lintResult = TemplateLint.lint(candidateTemplate)
        assertThat(lintResult).isInstanceOf(LintResult.Pass::class.java)

        // 4. Round-Trip валидация
        val expectedSlots = listOf(
            ExpectedSlot("amount", "17"),
            ExpectedSlot("curr", "MDL"),
            ExpectedSlot("mask", "6159"),
            ExpectedSlot("fee", "0.01"),
            ExpectedSlot("bal", "16.25"),
            ExpectedSlot("balcurr", "USD")
        )
        val roundTripResult = RoundTripValidator.validate(candidateTemplate, samplePush, expectedSlots)
        assertThat(roundTripResult).isEqualTo(RoundTripResult.Success)

        // 5. Проверяем извлечение групп RE2/J
        val re2 = Pattern.compile(candidateTemplate.pattern)
        val matcher = re2.matcher(samplePush)
        assertThat(matcher.find()).isTrue
        assertThat(matcher.group("amount")).isEqualTo("17")
        assertThat(matcher.group("curr")).isEqualTo("MDL")
        assertThat(matcher.group("mask")).isEqualTo("6159")
        assertThat(matcher.group("fee")).isEqualTo("0.01")
        assertThat(matcher.group("bal")).isEqualTo("16.25")
        assertThat(matcher.group("balcurr")).isEqualTo("USD")
    }
}
