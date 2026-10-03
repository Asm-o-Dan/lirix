package com.example.npc.induction

import com.example.npc.core.model.finance.CurrencyCode
import com.example.npc.core.model.finance.TransactionType
import com.example.npc.core.text.model.Token
import com.example.npc.core.text.model.TokenType
import com.example.npc.induction.model.AmountFormatSpec
import com.example.npc.induction.model.BuiltTemplate
import com.example.npc.induction.model.DecomposedTemplateSpec
import com.example.npc.induction.model.LiteralSegment
import com.example.npc.induction.model.OpTypeResolution
import com.example.npc.induction.model.Segment
import com.example.npc.induction.model.SegmentedSequence
import com.example.npc.induction.model.SlotSegment
import com.example.npc.induction.model.SlotType
import com.example.npc.induction.model.VariableSegment
import com.example.npc.induction.model.VariableType
import com.example.npc.induction.model.WhitespaceSegment
import com.google.re2j.Pattern

// Экспорт типов для удобства
typealias AmountFormatSpec = AmountFormatSpec
typealias BuiltTemplate = BuiltTemplate
typealias OpTypeResolution = OpTypeResolution
typealias DecomposedTemplateSpec = DecomposedTemplateSpec

/**
 * Синтезирует безопасный RE2/J шаблон (BuiltTemplate) с именованными группами захвата
 * (?P<amount>...), (?P<curr>...), (?P<mask >...)/(?P<card>...), (?P<merchant>...), (?P<bal>...)
 * на основе сегментированной последовательности токенов SegmentedSequence и библиотеки SafeFragments.
 */
object TemplateBuilder {

    /**
     * Построение BuiltTemplate по спецификации overview.md.
     */
    fun build(
        sequence: SegmentedSequence,
        opType: OpTypeResolution = OpTypeResolution(),
        defaultCurrency: CurrencyCode? = null,
        maskGroupName: String = "mask"
    ): BuiltTemplate {
        require(sequence.segments.isNotEmpty()) { "SegmentedSequence must not be empty" }

        val namedGroups = ArrayList<String>()
        val requiredLiterals = ArrayList<String>()
        val patternBuilder = StringBuilder()

        // 1. Паттерн всегда начинается с флага регистронезависимости (?i)
        patternBuilder.append("(?i)")

        val segments = sequence.segments

        for (idx in segments.indices) {
            val seg = segments[idx]

            when (seg) {
                is LiteralSegment -> {
                    appendLiteralSegment(
                        seg = seg,
                        sb = patternBuilder,
                        namedGroups = namedGroups,
                        requiredLiterals = requiredLiterals,
                        isFirst = patternBuilder.length == 4, // только "(?i)"
                        isLast = (idx == segments.size - 1)
                    )
                }

                is SlotSegment -> {
                    appendSlotSegment(
                        seg = seg,
                        sb = patternBuilder,
                        namedGroups = namedGroups,
                        maskGroupName = maskGroupName,
                        isLastSignificant = isLastSignificantSlot(segments, idx)
                    )
                }

                is VariableSegment -> {
                    appendVariableSegment(seg, patternBuilder)
                }

                is WhitespaceSegment -> {
                    appendWhitespaceSegment(seg, patternBuilder)
                }
            }
        }

        val rawPattern = patternBuilder.toString()

        // Проверка компилируемости в RE2/J
        try {
            Pattern.compile(rawPattern)
        } catch (e: Exception) {
            throw IllegalStateException("Generated regex pattern is not valid in RE2/J: $rawPattern", e)
        }

        // Семантические константы
        val constants = linkedMapOf(
            "isRefund" to opType.isRefund.toString(),
            "isDeclined" to opType.isDeclined.toString()
        ).apply {
            if (opType.transactionType != TransactionType.UNKNOWN) {
                put("transactionType", opType.transactionType.name)
            }
        }

        // Определение формата суммы
        val amountFormat = determineAmountFormat(sequence)

        return BuiltTemplate(
            pattern = rawPattern,
            namedGroups = namedGroups,
            constants = constants,
            amountFormat = amountFormat,
            requiredLiterals = requiredLiterals.distinct(),
            defaultCurrency = defaultCurrency
        )
    }

    /**
     * Перегрузка для сборки с явными семантическими константами.
     */
    fun build(
        sequence: SegmentedSequence,
        constants: Map<String, String>,
        defaultCurrency: CurrencyCode? = null,
        maskGroupName: String = "mask"
    ): BuiltTemplate {
        val opTypeName = constants["opType"] ?: constants["transactionType"]
        val txType = TransactionType.fromStringOrNull(opTypeName) ?: TransactionType.UNKNOWN
        val isRefund = constants["isRefund"]?.toBoolean() ?: false
        val isDeclined = constants["isDeclined"]?.toBoolean() ?: false

        val opType = OpTypeResolution(
            transactionType = txType,
            isRefund = isRefund,
            isDeclined = isDeclined
        )

        return build(sequence, opType, defaultCurrency, maskGroupName)
    }

    /**
     * Быстрое построение только строки регулярного выражения.
     */
    fun buildPattern(
        sequence: SegmentedSequence,
        maskGroupName: String = "mask"
    ): String = build(sequence, OpTypeResolution(), null, maskGroupName).pattern

    /**
     * Построение спецификации конвейера слотовых регулярок (Slot-Decomposed Regex Pipeline, OPT-PIPE-001).
     * 
     * @param sequence Сегментированная последовательность токенов
     * @param alternativeAnchors Список вариантов для якорного правила (например, listOf("Пополнение счета", "Перевод на карту", "Зачисление"))
     * @param opType Разрешение типа финансовой операции
     * @param defaultCurrency Валюта по умолчанию
     * @param customSlotRules Пользовательские правила для конкретных слотов (переопределяют стандартные компактные шаблоны)
     */
    fun buildDecomposedSpec(
        sequence: SegmentedSequence,
        alternativeAnchors: List<String> = emptyList(),
        opType: OpTypeResolution = OpTypeResolution(),
        defaultCurrency: CurrencyCode? = null,
        customSlotRules: Map<SlotType, String> = emptyMap()
    ): DecomposedTemplateSpec {
        // 1. Построение якорного правила (< 60 символов)
        val anchorPattern = if (alternativeAnchors.isNotEmpty()) {
            val joined = alternativeAnchors.joinToString("|") { opt ->
                if (opt.any { it in "[]{}()*+?^$\\." }) SafeFragments.quoteLiteral(opt) else opt
            }
            "(?i)(?:$joined)"
        } else {
            val firstAnchor = AnchorSelector.findFirstAnchor(sequence.segments)
            if (firstAnchor != null && firstAnchor.text.trim().isNotEmpty()) {
                val anchorText = if (firstAnchor.tokens.isNotEmpty()) {
                    buildGapAwareLiteral(firstAnchor.tokens)
                } else {
                    firstAnchor.text.trim().split(java.util.regex.Pattern.compile("\\s+")).joinToString("\\s+") { SafeFragments.quoteLiteral(it) }
                }
                "(?i)$anchorText"
            } else {
                val firstLit = sequence.segments.filterIsInstance<LiteralSegment>().firstOrNull { it.text.trim().isNotEmpty() }
                if (firstLit != null) {
                    val litText = if (firstLit.tokens.isNotEmpty()) {
                        buildGapAwareLiteral(firstLit.tokens)
                    } else {
                        firstLit.text.trim().split(java.util.regex.Pattern.compile("\\s+")).joinToString("\\s+") { SafeFragments.quoteLiteral(it) }
                    }
                    "(?i)$litText"
                } else {
                    "(?i).*"
                }
            }
        }

        // 2. Сбор слотовых правил для присутствующих в sequence слотов
        val rules = LinkedHashMap<SlotType, String>()
        val presentSlotTypes = sequence.segments.filterIsInstance<SlotSegment>().map { it.role }.distinct()

        for (slotType in presentSlotTypes) {
            val rule = customSlotRules[slotType] ?: SafeFragments.defaultSlotPatternFor(slotType)
            rules[slotType] = rule
        }

        // Добавляем customSlotRules, которые не были в sequence
        for ((slotType, customRule) in customSlotRules) {
            if (!rules.containsKey(slotType)) {
                rules[slotType] = customRule
            }
        }

        // Если в sequence вообще не было слотов, но заданы customSlotRules
        if (rules.isEmpty()) {
            rules[SlotType.AMOUNT] = customSlotRules[SlotType.AMOUNT] ?: SafeFragments.SLOT_AMOUNT_PATTERN
            rules[SlotType.CARD_MASK] = customSlotRules[SlotType.CARD_MASK] ?: SafeFragments.SLOT_CARD_MASK_PATTERN
            rules[SlotType.MERCHANT] = customSlotRules[SlotType.MERCHANT] ?: SafeFragments.SLOT_MERCHANT_PATTERN
            rules[SlotType.BALANCE] = customSlotRules[SlotType.BALANCE] ?: SafeFragments.SLOT_BALANCE_PATTERN
        }

        // 3. Литералы для быстрого префильтра
        val requiredLiterals = ArrayList<String>()
        if (alternativeAnchors.isNotEmpty()) {
            for (opt in alternativeAnchors) {
                val words = opt.split(java.util.regex.Pattern.compile("\\s+"))
                for (w in words) {
                    if (w.length >= 3 && w.any { it.isLetter() }) {
                        requiredLiterals.add(w.lowercase())
                    }
                }
            }
        } else {
            for (seg in sequence.segments) {
                if (seg is LiteralSegment) {
                    val words = seg.text.trim().split(java.util.regex.Pattern.compile("\\s+"))
                    for (w in words) {
                        if (w.length >= 3 && w.any { it.isLetter() }) {
                            requiredLiterals.add(w.lowercase())
                        }
                    }
                }
            }
        }

        val constants = linkedMapOf(
            "isRefund" to opType.isRefund.toString(),
            "isDeclined" to opType.isDeclined.toString()
        ).apply {
            if (opType.transactionType != TransactionType.UNKNOWN) {
                put("transactionType", opType.transactionType.name)
            }
        }

        return DecomposedTemplateSpec(
            anchorPattern = anchorPattern,
            slotRules = rules,
            constants = constants,
            requiredLiterals = requiredLiterals.distinct(),
            defaultCurrency = defaultCurrency
        )
    }

    /**
     * Построение BuiltTemplate с включенным decomposedSpec.
     */
    fun buildDecomposed(
        sequence: SegmentedSequence,
        alternativeAnchors: List<String> = emptyList(),
        opType: OpTypeResolution = OpTypeResolution(),
        defaultCurrency: CurrencyCode? = null,
        customSlotRules: Map<SlotType, String> = emptyMap()
    ): BuiltTemplate {
        val spec = buildDecomposedSpec(
            sequence = sequence,
            alternativeAnchors = alternativeAnchors,
            opType = opType,
            defaultCurrency = defaultCurrency,
            customSlotRules = customSlotRules
        )

        val amountFormat = determineAmountFormat(sequence)
        val namedGroups = spec.slotRules.keys.map { it.name.lowercase() }

        return BuiltTemplate(
            pattern = spec.anchorPattern,
            namedGroups = namedGroups,
            constants = spec.constants,
            amountFormat = amountFormat,
            requiredLiterals = spec.requiredLiterals,
            defaultCurrency = defaultCurrency,
            decomposedSpec = spec
        )
    }

    private val CARD_MASK_REGEX = java.util.regex.Pattern.compile(
        """^(?:(?:\*+|[•]+|\d{4})\d{4}|\*{2,4}\d{4}|[*•·]{1,8}\d{2,6}|(?:\.{2,4})\d{4})$"""
    )
    private val BALANCE_KEYWORD_REGEX = java.util.regex.Pattern.compile(
        """(?i)^(?:доступный|текущий|disponibil)?(?:остаток|баланс|sold|balance):?$|^(?:остаток|баланс|sold|balance):?$"""
    )
    private val NUMBER_REGEX = java.util.regex.Pattern.compile(
        """^[+\-]?[0-9]+(?:[ .,'][0-9]{3})*(?:[.,][0-9]{1,2})?$"""
    )

    private fun isCardMask(text: String): Boolean {
        val clean = text.trim().trimEnd('.', ',', ';', ':')
        return CARD_MASK_REGEX.matcher(clean).matches()
    }

    private fun isBalanceKeyword(text: String): Boolean {
        val clean = text.trim()
        return BALANCE_KEYWORD_REGEX.matcher(clean).matches()
    }

    private fun isNumber(text: String): Boolean {
        val clean = text.trim().trimEnd('.', ',', ';', ':')
        return NUMBER_REGEX.matcher(clean).matches()
    }

    private fun isPunctuationToken(text: String): Boolean {
        val trimmed = text.trim()
        return trimmed.isNotEmpty() && trimmed.all { it in ".,:;!?\"'()[]{}-/\\" }
    }

    private fun buildGapAwareLiteral(tokens: List<Token>): String {
        if (tokens.isEmpty()) return ""
        val sb = StringBuilder()
        for (tIdx in tokens.indices) {
            val tok = tokens[tIdx]
            if (tIdx > 0) {
                val prevTok = tokens[tIdx - 1]
                val isGap = tok.span.start > prevTok.span.end
                val isPunctOrSign = tok.type == TokenType.PUNCT || tok.type == TokenType.SIGN || isPunctuationToken(tok.text)

                if (!isGap) {
                    // 1. Нет зазора между токенами (например TEMU + . + COM) -> пустая строка, БЕЗ \s+
                } else if (isPunctOrSign) {
                    // 3. Перед пунктуацией при наличии зазора эмитим \s* для устойчивости
                    appendSeparator(sb, "\\s*")
                } else {
                    // 2. Между словами/числами при наличии зазора эмитим \s+
                    appendSeparator(sb, "\\s+")
                }
            }
            sb.append(SafeFragments.quoteLiteral(tok.text))
        }
        return sb.toString()
    }

    private fun appendLiteralSegment(
        seg: LiteralSegment,
        sb: StringBuilder,
        namedGroups: MutableList<String>,
        requiredLiterals: MutableList<String>,
        isFirst: Boolean,
        isLast: Boolean
    ) {
        val trimmed = seg.text.trim()
        if (trimmed.isEmpty()) {
            if (seg.text.contains('\n')) {
                appendSeparator(sb, "(?:\\n|\\s+)")
            } else if (!isFirst && !isLast) {
                appendSeparator(sb, "\\s+")
            }
            return
        }

        // Если перед литералом был пробел в seg.text или в конце буфера нет разделителя
        val hasLeadingSpace = seg.text.startsWith(' ') || seg.text.startsWith('\n')
        if (hasLeadingSpace && !isFirst) {
            val firstTok = seg.tokens.firstOrNull()
            if (firstTok != null && (firstTok.type == TokenType.PUNCT || firstTok.type == TokenType.SIGN || isPunctuationToken(firstTok.text))) {
                appendSeparator(sb, "\\s*")
            } else {
                appendSeparator(sb, "\\s+")
            }
        }

        var awaitingBalanceNumber = false
        var balanceKeywordCount = 0
        var awaitingBalanceCurrency = false

        if (seg.tokens.isNotEmpty()) {
            for (tIdx in seg.tokens.indices) {
                val tok = seg.tokens[tIdx]
                if (tIdx > 0) {
                    val prevTok = seg.tokens[tIdx - 1]
                    val isGap = tok.span.start > prevTok.span.end
                    val isPunctOrSign = tok.type == TokenType.PUNCT || tok.type == TokenType.SIGN || isPunctuationToken(tok.text)

                    if (!isGap) {
                        // 1. Нет зазора между токенами (например TEMU + . + COM) -> пустая строка, БЕЗ \s+
                    } else if (isPunctOrSign) {
                        // 3. Перед пунктуацией при наличии зазора эмитим \s* для устойчивости
                        appendSeparator(sb, "\\s*")
                    } else {
                        // 2. Между словами/числами при наличии зазора эмитим \s+
                        appendSeparator(sb, "\\s+")
                    }
                }

                // 1. Автоматическое распознавание неразмеченных масок карт
                if (tok.type == TokenType.CARD_MASK || isCardMask(tok.text)) {
                    val raw = tok.text.trim()
                    val clean = raw.trimEnd('.', ',', ';', ':')
                    val trailingPunct = raw.substring(clean.length)
                    sb.append("(?<CARD_MASK>[*•\\d]{4,16})")
                    if (trailingPunct.isNotEmpty()) {
                        sb.append(SafeFragments.quoteLiteral(trailingPunct))
                    }
                    if (!namedGroups.contains("CARD_MASK")) namedGroups.add("CARD_MASK")
                    if (!namedGroups.contains("mask")) namedGroups.add("mask")
                    if (!namedGroups.contains("card")) namedGroups.add("card")
                    continue
                }

                // 2. Автоматическое распознавание баланса/остатка после ключевых слов
                if (awaitingBalanceNumber && (tok.type == TokenType.NUMBER || isNumber(tok.text))) {
                    val raw = tok.text.trim()
                    val clean = raw.trimEnd('.', ',', ';', ':')
                    val trailingPunct = raw.substring(clean.length)
                    sb.append("(?<BALANCE>[\\d\\s.,]+)")
                    if (trailingPunct.isNotEmpty()) {
                        sb.append(SafeFragments.quoteLiteral(trailingPunct))
                    }
                    if (!namedGroups.contains("BALANCE")) namedGroups.add("BALANCE")
                    if (!namedGroups.contains("bal")) namedGroups.add("bal")
                    awaitingBalanceNumber = false
                    balanceKeywordCount = 0
                    awaitingBalanceCurrency = true
                    continue
                }

                if (awaitingBalanceCurrency && (tok.type == TokenType.CURRENCY || isLikelyCurrency(tok.text))) {
                    val raw = tok.text.trim()
                    val clean = raw.trimEnd('.', ',', ';', ':')
                    val trailingPunct = raw.substring(clean.length)
                    sb.append("(?<balcurr>${SafeFragments.CURRENCY})")
                    if (trailingPunct.isNotEmpty()) {
                        sb.append(SafeFragments.quoteLiteral(trailingPunct))
                    }
                    if (!namedGroups.contains("balcurr")) namedGroups.add("balcurr")
                    awaitingBalanceCurrency = false
                    continue
                }

                if (isBalanceKeyword(tok.text)) {
                    awaitingBalanceNumber = true
                    balanceKeywordCount = 3
                } else if (awaitingBalanceNumber) {
                    if (tok.type != TokenType.PUNCT && tok.type != TokenType.SIGN) {
                        balanceKeywordCount--
                        if (balanceKeywordCount <= 0) awaitingBalanceNumber = false
                    }
                } else if (awaitingBalanceCurrency) {
                    if (tok.type != TokenType.PUNCT && tok.type != TokenType.SIGN) {
                        awaitingBalanceCurrency = false
                    }
                }

                // Учитываем литералы >= 3 букв для быстрого префильтра в рантайме
                val cleanWord = tok.text.trimEnd('.', ',', ';', ':')
                if (cleanWord.length >= 3 && cleanWord.any { it.isLetter() }) {
                    requiredLiterals.add(cleanWord.lowercase())
                }

                // Серии пробелов внутри токена экранируем как \s+
                if (tok.text.contains(' ')) {
                    val subWords = tok.text.split(java.util.regex.Pattern.compile("\\s+"))
                    for (swIdx in subWords.indices) {
                        if (swIdx > 0) appendSeparator(sb, "\\s+")
                        sb.append(SafeFragments.quoteLiteral(subWords[swIdx]))
                    }
                } else {
                    sb.append(SafeFragments.quoteLiteral(tok.text))
                }
            }
        } else {
            // Без токенов — экранируем trimmed текст, разбивая серии пробелов на \s+
            val words = trimmed.split(java.util.regex.Pattern.compile("\\s+"))
            for (wIdx in words.indices) {
                val w = words[wIdx]
                if (wIdx > 0) {
                    appendSeparator(sb, "\\s+")
                }

                // 1. Неразмеченная маска карты
                if (isCardMask(w)) {
                    val clean = w.trimEnd('.', ',', ';', ':')
                    val trailingPunct = w.substring(clean.length)
                    sb.append("(?<CARD_MASK>[*•\\d]{4,16})")
                    if (trailingPunct.isNotEmpty()) {
                        sb.append(SafeFragments.quoteLiteral(trailingPunct))
                    }
                    if (!namedGroups.contains("CARD_MASK")) namedGroups.add("CARD_MASK")
                    if (!namedGroups.contains("mask")) namedGroups.add("mask")
                    if (!namedGroups.contains("card")) namedGroups.add("card")
                    continue
                }

                // 2. Неразмеченный баланс
                if (awaitingBalanceNumber && isNumber(w)) {
                    val clean = w.trimEnd('.', ',', ';', ':')
                    val trailingPunct = w.substring(clean.length)
                    sb.append("(?<BALANCE>[\\d\\s.,]+)")
                    if (trailingPunct.isNotEmpty()) {
                        sb.append(SafeFragments.quoteLiteral(trailingPunct))
                    }
                    if (!namedGroups.contains("BALANCE")) namedGroups.add("BALANCE")
                    if (!namedGroups.contains("bal")) namedGroups.add("bal")
                    awaitingBalanceNumber = false
                    awaitingBalanceCurrency = true
                    continue
                }

                if (awaitingBalanceCurrency && isLikelyCurrency(w)) {
                    val clean = w.trimEnd('.', ',', ';', ':')
                    val trailingPunct = w.substring(clean.length)
                    sb.append("(?<balcurr>${SafeFragments.CURRENCY})")
                    if (trailingPunct.isNotEmpty()) {
                        sb.append(SafeFragments.quoteLiteral(trailingPunct))
                    }
                    if (!namedGroups.contains("balcurr")) namedGroups.add("balcurr")
                    awaitingBalanceCurrency = false
                    continue
                }

                if (isBalanceKeyword(w)) {
                    awaitingBalanceNumber = true
                } else if (awaitingBalanceCurrency) {
                    awaitingBalanceCurrency = false
                }

                val cleanWord = w.trimEnd('.', ',', ';', ':')
                if (cleanWord.length >= 3 && cleanWord.any { it.isLetter() }) {
                    requiredLiterals.add(cleanWord.lowercase())
                }
                sb.append(SafeFragments.quoteLiteral(w))
            }
        }

        // Если после литерала есть пробел
        val hasTrailingSpace = seg.text.endsWith(' ') || seg.text.endsWith('\n')
        if (hasTrailingSpace && !isLast) {
            appendSeparator(sb, "\\s+")
        }
    }

    private fun appendSlotSegment(
        seg: SlotSegment,
        sb: StringBuilder,
        namedGroups: MutableList<String>,
        maskGroupName: String,
        isLastSignificant: Boolean
    ) {
        // Гарантируем разделитель перед слотом, если предыдущий символ не разделитель
        ensureSeparatorBeforeSlot(sb)

        when (seg.role) {
            SlotType.TX_AMOUNT, SlotType.AMOUNT -> {
                val hasCurrency = seg.tokens.any { it.type == TokenType.CURRENCY } ||
                    seg.text.split(" ").any { isLikelyCurrency(it) }

                if (hasCurrency) {
                    sb.append("(?P<amount>${SafeFragments.AMOUNT})\\s*(?P<curr>${SafeFragments.CURRENCY})")
                    namedGroups.add("amount")
                    namedGroups.add("curr")
                } else {
                    sb.append("(?P<amount>${SafeFragments.AMOUNT})")
                    namedGroups.add("amount")
                }
            }

            SlotType.CURRENCY -> {
                sb.append("(?P<curr>${SafeFragments.CURRENCY})")
                namedGroups.add("curr")
            }

            SlotType.BALANCE -> {
                val hasCurrency = seg.tokens.any { it.type == TokenType.CURRENCY } ||
                    seg.text.split(" ").any { isLikelyCurrency(it) }

                if (hasCurrency) {
                    sb.append("(?P<bal>${SafeFragments.AMOUNT})\\s*(?P<balcurr>${SafeFragments.CURRENCY})")
                    namedGroups.add("bal")
                    if (!namedGroups.contains("BALANCE")) namedGroups.add("BALANCE")
                    namedGroups.add("balcurr")
                } else {
                    sb.append("(?P<bal>${SafeFragments.AMOUNT})")
                    namedGroups.add("bal")
                    if (!namedGroups.contains("BALANCE")) namedGroups.add("BALANCE")
                }
            }

            SlotType.BALANCE_CURRENCY -> {
                sb.append("(?P<balcurr>${SafeFragments.CURRENCY})")
                namedGroups.add("balcurr")
            }

            SlotType.CARD_MASK -> {
                val group = if (seg.slotName.equals("card", ignoreCase = true)) "card" else maskGroupName
                val rawText = seg.text.trim()

                // Отделяем префикс (*, card ..., ..) от 4 цифр маски
                val match4 = java.util.regex.Pattern.compile("^(.*?)([0-9]{4})$").matcher(rawText)
                if (match4.matches()) {
                    val prefix = match4.group(1).trim()
                    if (prefix.isNotEmpty()) {
                        var hasSymbols = false
                        for (c in prefix) {
                            if (c == '*' || c == '•' || c == '·') {
                                hasSymbols = true
                            }
                        }
                        if (hasSymbols) {
                            sb.append("[*•·]{1,8}")
                        } else {
                            for (c in prefix) {
                                if (c == '.') {
                                    sb.append("\\.")
                                } else if (c == ' ') {
                                    appendSeparator(sb, "\\s*")
                                } else {
                                    sb.append(SafeFragments.quoteLiteral(c.toString()))
                                }
                            }
                        }
                    } else {
                        sb.append("[*•·]{1,8}")
                    }
                    sb.append("(?P<$group>${SafeFragments.CARD4})")
                } else {
                    sb.append("[*•·]{1,8}(?P<$group>${SafeFragments.CARD4})")
                }
                namedGroups.add(group)
                if (!namedGroups.contains("CARD_MASK")) namedGroups.add("CARD_MASK")
                if (!namedGroups.contains("card")) namedGroups.add("card")
                if (!namedGroups.contains("mask")) namedGroups.add("mask")
            }

            SlotType.MERCHANT -> {
                sb.append("(?P<merchant>${SafeFragments.MERCHANT})")
                namedGroups.add("merchant")
                // Правая ограниченность (Right-Bounded Rule)
                if (isLastSignificant) {
                    sb.append(SafeFragments.LINE_END)
                }
            }

            SlotType.FEE -> {
                sb.append("(?P<fee>${SafeFragments.AMOUNT})")
                namedGroups.add("fee")
            }

            SlotType.OTHER -> {
                sb.append("(?P<other>[^\\n]{1,64}?)")
                namedGroups.add("other")
            }
        }
    }

    private fun appendVariableSegment(seg: VariableSegment, sb: StringBuilder) {
        ensureSeparatorBeforeSlot(sb)
        when (seg.variableType) {
            VariableType.DATE -> sb.append("(?:${SafeFragments.DATE})")
            VariableType.TIME -> sb.append("(?:${SafeFragments.TIME})")
            VariableType.REFERENCE_CODE -> sb.append("(?:[A-Za-z0-9]{4,32})")
        }
    }

    private fun appendWhitespaceSegment(seg: WhitespaceSegment, sb: StringBuilder) {
        if (seg.hasNewline) {
            appendSeparator(sb, "(?:\\n|\\s+)")
        } else {
            appendSeparator(sb, "\\s+")
        }
    }

    private fun ensureSeparatorBeforeSlot(sb: StringBuilder) {
        if (sb.length > 4) { // Больше чем "(?i)"
            if (!sb.endsWith("\\s+") && !sb.endsWith("\\s*") && !sb.endsWith("(?:\\n|\\s+)") && !sb.endsWith(":")) {
                sb.append("\\s+")
            }
        }
    }

    private fun appendSeparator(sb: StringBuilder, separator: String) {
        if (separator.isEmpty()) return
        if (sb.endsWith(separator)) return
        if (sb.endsWith("\\s+") && separator == "\\s*") return
        if (sb.endsWith("\\s*") && separator == "\\s+") {
            sb.setLength(sb.length - 3)
            sb.append("\\s+")
            return
        }
        if (sb.endsWith("(?:\\n|\\s+)") && (separator == "\\s+" || separator == "\\s*")) return
        sb.append(separator)
    }

    private fun isLastSignificantSlot(segments: List<Segment>, currentIndex: Int): Boolean {
        for (i in (currentIndex + 1) until segments.size) {
            val s = segments[i]
            if (s is LiteralSegment && s.text.trim().isNotEmpty()) return false
            if (s is SlotSegment) return false
            if (s is VariableSegment) return false
        }
        return true
    }

    private fun isLikelyCurrency(str: String): Boolean {
        val upper = str.uppercase()
        return upper in setOf("MDL", "RUP", "USD", "EUR", "RUB", "LEI", "ЛЕЙ", "ЛЕЕВ", "РУБ", "РУБ.", "Р.", "$", "€")
    }

    private fun determineAmountFormat(sequence: SegmentedSequence): AmountFormatSpec {
        for (seg in sequence.segments) {
            if (seg is SlotSegment && (seg.role == SlotType.AMOUNT || seg.role == SlotType.TX_AMOUNT || seg.role == SlotType.BALANCE)) {
                // Проверяем интерпретацию токена, если есть
                val numToken = seg.tokens.firstOrNull { it.type == TokenType.NUMBER }
                if (numToken != null && numToken.interpretations.isNotEmpty()) {
                    val interp = numToken.interpretations.first()
                    return AmountFormatSpec(
                        decimalSeparator = interp.decimalSeparator,
                        groupingSeparator = interp.groupingSeparator
                    )
                }

                // Парсим из текста слота
                val text = seg.text
                var decimalSep: Char? = null
                var groupingSep: Char? = null

                if (text.contains(',') && text.contains('.')) {
                    val lastComma = text.lastIndexOf(',')
                    val lastDot = text.lastIndexOf('.')
                    if (lastComma > lastDot) {
                        decimalSep = ','
                        groupingSep = '.'
                    } else {
                        decimalSep = '.'
                        groupingSep = ','
                    }
                } else if (text.contains(' ') && (text.contains(',') || text.contains('.'))) {
                    groupingSep = ' '
                    decimalSep = if (text.contains(',')) ',' else '.'
                } else if (text.contains(',')) {
                    val parts = text.split(',')
                    if (parts.size == 2 && parts[1].filter { it.isDigit() }.length in 1..2) {
                        decimalSep = ','
                    }
                } else if (text.contains('.')) {
                    val parts = text.split('.')
                    if (parts.size == 2 && parts[1].filter { it.isDigit() }.length in 1..2) {
                        decimalSep = '.'
                    }
                }

                return AmountFormatSpec(decimalSep, groupingSep)
            }
        }
        return AmountFormatSpec(null, null)
    }
}
