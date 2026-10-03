package com.example.npc.induction.model

import com.example.npc.core.model.finance.CurrencyCode
import com.example.npc.core.model.finance.TransactionType
import com.example.npc.core.text.TextSpan
import com.example.npc.core.text.model.Token

/**
 * Роли токенов при сегментации шаблона.
 */
enum class TokenRole {
    LITERAL,        // Фиксированный текст (Pattern.quote)
    SLOT,           // Именованная группа захвата (Amount, Currency, Card...)
    VARIABLE,       // Обобщаемая переменная (Дата, Служебный номер)
    WHITESPACE      // Разделитель
}

/**
 * Семантические типы слотов для именованных групп RE2/J.
 */
enum class SlotType {
    AMOUNT,
    CURRENCY,
    CARD_MASK,
    BALANCE,
    BALANCE_CURRENCY,
    MERCHANT,
    FEE,
    OTHER,
    TX_AMOUNT
}

typealias SlotRole = SlotType

/**
 * Назначение роли слота непрерывной группе токенов.
 */
data class SlotAssignment(
    val slot: SlotType,
    val tokenIndices: List<Int>,
    val span: TextSpan = TextSpan(0, 0)
) {
    constructor(slot: SlotType, tokenIndex: Int, span: TextSpan = TextSpan(0, 0)) :
        this(slot, listOf(tokenIndex), span)
}

/**
 * Типы обобщаемых переменных сущностей.
 */
enum class VariableType {
    DATE,
    TIME,
    REFERENCE_CODE
}

/**
 * Базовый интерфейс функционального сегмента последовательности.
 */
sealed interface Segment {
    val tokens: List<Token>
    val text: String
}

data class LiteralSegment(
    override val tokens: List<Token>,
    override val text: String
) : Segment {
    constructor(text: String) : this(emptyList(), text)
}

data class SlotSegment(
    val slotName: String,
    val role: SlotType,
    override val tokens: List<Token>,
    override val text: String
) : Segment {
    val slot: SlotType get() = role

    constructor(
        slot: SlotType,
        tokens: List<Token>,
        text: String,
        slotName: String = slot.name
    ) : this(slotName = slotName, role = slot, tokens = tokens, text = text)

    constructor(
        slot: SlotType,
        text: String,
        tokens: List<Token> = emptyList(),
        slotName: String = slot.name
    ) : this(slotName = slotName, role = slot, tokens = tokens, text = text)
}

data class VariableSegment(
    val variableType: VariableType,
    override val tokens: List<Token>,
    override val text: String
) : Segment {
    constructor(variableType: VariableType, text: String) : this(variableType, emptyList(), text)
}

data class WhitespaceSegment(
    val hasNewline: Boolean,
    override val tokens: List<Token> = emptyList(),
    override val text: String
) : Segment {
    constructor(text: String, hasNewline: Boolean = text.contains('\n')) : this(hasNewline, emptyList(), text)
}

/**
 * Результат сегментации потока токенов.
 */
data class SegmentedSequence(
    val segments: List<Segment>,
    val totalLength: Int
)

/**
 * Спецификация формата чисел (десятичный и группирующий разделители).
 */
data class AmountFormatSpec(
    val decimalSeparator: Char? = null,
    val groupingSeparator: Char? = null
)

/**
 * Разрешение семантического типа финансовой операции.
 */
data class OpTypeResolution(
    val transactionType: TransactionType = TransactionType.DEBIT,
    val isRefund: Boolean = false,
    val isDeclined: Boolean = false,
    val dominantKeyword: Token? = null,
    val confidence: Float = 1.0f
)

/**
 * Спецификация конвейера слотовых регулярок (Slot-Decomposed Regex Pipeline, OPT-PIPE-001).
 * Разделяет шаблон на компактное якорное правило и независимые слотовые экстракторы:
 * - anchorPattern: быстрая детекция принадлежности (< 60 символов)
 * - slotRules: независимые правила извлечения слотов (30-70 символов)
 */
data class DecomposedTemplateSpec(
    val anchorPattern: String,
    val slotRules: Map<SlotType, String>,
    val constants: Map<String, String> = emptyMap(),
    val requiredLiterals: List<String> = emptyList(),
    val defaultCurrency: CurrencyCode? = null
) {
    val slotRulesByName: Map<String, String>
        get() = slotRules.mapKeys { it.key.name.lowercase() }

    companion object {
        fun fromNamedRules(
            anchorPattern: String,
            slotRulesByName: Map<String, String>,
            constants: Map<String, String> = emptyMap(),
            requiredLiterals: List<String> = emptyList(),
            defaultCurrency: CurrencyCode? = null
        ): DecomposedTemplateSpec {
            val rules = slotRulesByName.mapNotNull { (k, v) ->
                val type = SlotType.values().firstOrNull { it.name.equals(k, ignoreCase = true) }
                if (type != null) type to v else null
            }.toMap()
            return DecomposedTemplateSpec(
                anchorPattern = anchorPattern,
                slotRules = rules,
                constants = constants,
                requiredLiterals = requiredLiterals,
                defaultCurrency = defaultCurrency
            )
        }
    }
}

/**
 * Скомпилированное описание шаблона с именованными группами RE2/J и метаданными.
 */
data class BuiltTemplate(
    val pattern: String,
    val namedGroups: List<String>,
    val constants: Map<String, String>,
    val amountFormat: AmountFormatSpec,
    val requiredLiterals: List<String>,
    val defaultCurrency: CurrencyCode? = null,
    val decomposedSpec: DecomposedTemplateSpec? = null
)
