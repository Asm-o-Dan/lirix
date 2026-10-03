package com.example.npc.extract.universal.model

import com.example.npc.core.model.finance.CurrencyCode
import com.example.npc.core.text.TextSpan

/**
 * Базовый маркерный интерфейс для всех сущностей-кандидатов, обнаруженных в потоке токенов.
 */
sealed interface Candidate {
    val span: TextSpan
    val tokenIndex: Int
}

/**
 * Кандидат на денежную сумму, строго удовлетворяющий правилу связки с валютой (ADR-302).
 */
data class AmountCandidate(
    val id: Int,
    override val tokenIndex: Int,
    val currencyTokenIndex: Int,
    val minorUnits: Long,
    val currencyCode: CurrencyCode,
    val isPrefixCurrency: Boolean,
    val distanceTokens: Int,
    override val span: TextSpan
) : Candidate {
    init {
        require(minorUnits >= 0L) { "Amount minor units must be non-negative: $minorUnits" }
        require(distanceTokens in 1..2) {
            "Distance between number and currency must be strictly 1 or 2 tokens: $distanceTokens"
        }
    }
}
